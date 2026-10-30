"""
Grid-based BPMN 2.0 writer for Camunda 8 (Zeebe) models.

Why a generator: hand-placing coordinates leads to crooked, overlapping diagrams.
Here every element sits on a (lane, row, column) grid and sequence flows are routed
orthogonally, so each diagram follows the readability rules in docs/bpmn-style-guide.md:
happy path on one straight line, exceptions branch up/down, no crossing flows,
labelled gateways and outcomes, colour-coded task kinds.
"""
from xml.sax.saxutils import escape, quoteattr

TASK_W, TASK_H = 100, 80
EVENT_D = 36
GW_D = 50

# (stroke, fill) — same palette in every lab, explained in the style guide / legend.
PALETTE = {
    "human": ("#e65100", "#ffe0b2"),     # user tasks (Tasklist)
    "rest": ("#1565c0", "#bbdefb"),      # REST connector tasks
    "dmn": ("#6a1b9a", "#e1bee7"),       # business rule (DMN) tasks
    "worker": ("#2e7d32", "#e8f5e9"),    # Java job-worker tasks
    "notify": ("#455a64", "#eceff1"),    # notifications (send tasks)
    "success": ("#2e7d32", "#c8e6c9"),   # successful end events
    "failure": ("#c62828", "#ffcdd2"),   # rejection / cancellation end events
    "neutral": ("#455a64", "#eceff1"),   # informational end events
    "alert": ("#c62828", "#ffffff"),     # error / timer boundary events
    "start": ("#2e7d32", "#ffffff"),
}

TAGS = {
    "start": "startEvent", "end": "endEvent", "boundary": "boundaryEvent",
    "service": "serviceTask", "user": "userTask", "rule": "businessRuleTask",
    "send": "sendTask", "xor": "exclusiveGateway", "and": "parallelGateway",
}


def _attr(v):
    return quoteattr(str(v))


class Element:
    def __init__(self, id, kind, name, lane=None, col=0, row=0, color=None, ext="",
                 doc=None, label="bottom", attrs=None, event_def="", attached_to=None,
                 side="bottom", offset=0, label_dx=0, label_dy=0, label_w=None):
        self.id, self.kind, self.name = id, kind, name
        self.lane, self.col, self.row = lane, col, row
        self.color, self.ext, self.doc = color, ext, doc
        self.label, self.attrs = label, attrs or {}
        self.event_def, self.attached_to = event_def, attached_to
        self.side, self.offset = side, offset
        self.label_dx, self.label_dy, self.label_w = label_dx, label_dy, label_w
        self.incoming, self.outgoing = [], []
        self.cx = self.cy = 0

    @property
    def size(self):
        if self.kind in ("start", "end", "boundary"):
            return EVENT_D, EVENT_D
        if self.kind in ("xor", "and"):
            return GW_D, GW_D
        return TASK_W, TASK_H

    def anchor(self, side):
        w, h = self.size
        return {
            "left": (self.cx - w / 2, self.cy), "right": (self.cx + w / 2, self.cy),
            "top": (self.cx, self.cy - h / 2), "bottom": (self.cx, self.cy + h / 2),
        }[side]


class Flow:
    def __init__(self, id, src, tgt, name=None, condition=None, exit=None, entry=None,
                 via=None, label_at=None, default=False):
        self.id, self.src, self.tgt, self.name = id, src, tgt, name
        self.condition, self.exit, self.entry = condition, exit, entry
        self.via, self.label_at, self.default = via or [], label_at, default
        self.points = []


class Diagram:
    """One pool (participant) with horizontal lanes, each lane split into rows."""

    def __init__(self, process_id, process_name, pool_name, lanes, *, col_w=160,
                 row_h=120, x0=160, y0=80, doc=None, version_tag=None):
        self.pid, self.pname, self.pool_name = process_id, process_name, pool_name
        self.lanes = lanes  # list of (lane_id, lane_name, number_of_rows)
        self.col_w, self.row_h, self.x0, self.y0 = col_w, row_h, x0, y0
        self.doc, self.version_tag = doc, version_tag
        self.elements, self.flows, self.annotations = {}, [], []
        self.errors, self.messages = [], []
        self.lane_top = {}
        y = y0
        for lid, _, rows in lanes:
            self.lane_top[lid] = y
            y += rows * row_h
        self.pool_h = y - y0

    # ---- grid helpers ---------------------------------------------------
    def x(self, col):
        return self.x0 + 30 + 30 + 70 + col * self.col_w

    def y(self, lane, row):
        return self.lane_top[lane] + self.row_h / 2 + row * self.row_h

    # ---- element factories ---------------------------------------------
    def add(self, el):
        assert el.id not in self.elements, el.id
        self.elements[el.id] = el
        return el

    def start(self, id, name, lane, col, row=0, **kw):
        kw.setdefault("color", "start")
        return self.add(Element(id, "start", name, lane, col, row, **kw))

    def end(self, id, name, lane, col, row=0, color="success", **kw):
        return self.add(Element(id, "end", name, lane, col, row, color=color, **kw))

    def task(self, id, name, lane, col, row=0, kind="service", color=None, **kw):
        if color is None:
            color = {"service": "worker", "user": "human", "rule": "dmn", "send": "notify"}[kind]
        return self.add(Element(id, kind, name, lane, col, row, color=color, **kw))

    def xor(self, id, name, lane, col, row=0, **kw):
        return self.add(Element(id, "xor", name, lane, col, row, **kw))

    def boundary(self, id, name, attached_to, event_def, side="bottom", offset=0,
                 interrupting=True, **kw):
        kw.setdefault("color", "alert")
        attrs = {"attachedToRef": attached_to}
        if not interrupting:
            attrs["cancelActivity"] = "false"
        return self.add(Element(id, "boundary", name, attached_to=attached_to,
                                event_def=event_def, side=side, offset=offset,
                                attrs=attrs, **kw))

    def flow(self, id, src, tgt, name=None, **kw):
        f = Flow(id, src, tgt, name, **kw)
        self.flows.append(f)
        return f

    def annotate(self, id, text, x, y, w=220, h=None, attach_to=None):
        lines = text.count("\n") + 1
        self.annotations.append((id, text, x, y, w, h or 16 * lines + 12, attach_to))

    def error(self, id, name, code):
        self.errors.append((id, name, code))

    # ---- layout ----------------------------------------------------------
    def _place(self):
        for el in self.elements.values():
            if el.kind == "boundary":
                host = self.elements[el.attached_to]
                hw, hh = host.size
                if el.side == "bottom":
                    el.cx, el.cy = host.cx + el.offset, host.cy + hh / 2
                else:
                    el.cx, el.cy = host.cx + el.offset, host.cy - hh / 2
            else:
                el.cx, el.cy = self.x(el.col), self.y(el.lane, el.row)
        # place boundary events again (hosts placed in first pass may come later)
        for el in self.elements.values():
            if el.kind == "boundary":
                host = self.elements[el.attached_to]
                hh = host.size[1]
                el.cx = host.cx + el.offset
                el.cy = host.cy + (hh / 2 if el.side == "bottom" else -hh / 2)

    def _route(self, f):
        s, t = self.elements[f.src], self.elements[f.tgt]
        exit_side, entry_side = f.exit, f.entry
        if exit_side is None:
            if s.kind == "boundary":
                exit_side = s.side
            elif s.kind in ("xor", "and") and abs(s.cy - t.cy) > 1 and not f.via:
                exit_side = "bottom" if t.cy > s.cy else "top"
            else:
                exit_side = "right"
        if entry_side is None:
            if t.kind in ("xor", "and") and abs(s.cy - t.cy) > 1 and exit_side == "right" and not f.via:
                entry_side = "bottom" if s.cy > t.cy else "top"
            else:
                entry_side = "left"
        p0, p1 = s.anchor(exit_side), t.anchor(entry_side)
        pts = [p0]
        if f.via:
            pts += f.via
        elif exit_side in ("top", "bottom") and entry_side == "left":
            if abs(p0[0] - p1[0]) > 1:
                pts.append((p0[0], p1[1]))
        elif exit_side == "right" and entry_side in ("top", "bottom"):
            pts.append((p1[0], p0[1]))
        elif exit_side == "right" and entry_side == "left" and abs(p0[1] - p1[1]) > 1:
            mx = (p0[0] + p1[0]) / 2
            pts += [(mx, p0[1]), (mx, p1[1])]
        pts.append(p1)
        f.points = pts

    def _flow_label(self, f):
        if f.label_at:
            x, y = f.label_at
            return x, y
        (x0, y0), (x1, y1) = f.points[0], f.points[1]
        if abs(y0 - y1) < 1:  # first segment horizontal -> label above it
            return x0 + 8, y0 - 22
        if y1 > y0:  # going down -> label right of the line
            return x0 + 6, y0 + 6
        return x0 + 6, y0 - 26

    # ---- XML -----------------------------------------------------------
    def to_xml(self):
        self._place()
        for f in self.flows:
            self.elements[f.src].outgoing.append(f.id)
            self.elements[f.tgt].incoming.append(f.id)
            self._route(f)
        defaults = {f.src: f.id for f in self.flows if f.default}

        o = []
        o.append('<?xml version="1.0" encoding="UTF-8"?>')
        o.append(
            '<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" '
            'xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" '
            'xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" '
            'xmlns:di="http://www.omg.org/spec/DD/20100524/DI" '
            'xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" '
            'xmlns:modeler="http://camunda.org/schema/modeler/1.0" '
            'xmlns:bioc="http://bpmn.io/schema/bpmn/biocolor/1.0" '
            'xmlns:color="http://www.omg.org/spec/BPMN/non-normative/color/1.0" '
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
            f'id="Definitions_{self.pid}" targetNamespace="http://bpmn.io/schema/bpmn" '
            'exporter="Camunda Modeler" exporterVersion="5.49.0" '
            'modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.8.0">')
        o.append(f'  <bpmn:collaboration id="Collaboration_{self.pid}">')
        o.append(f'    <bpmn:participant id="Participant_{self.pid}" name={_attr(self.pool_name)} processRef="{self.pid}" />')
        for aid, *_rest in self.annotations:
            pass
        o.append('  </bpmn:collaboration>')

        o.append(f'  <bpmn:process id="{self.pid}" name={_attr(self.pname)} isExecutable="true">')
        if self.doc:
            o.append(f'    <bpmn:documentation>{escape(self.doc)}</bpmn:documentation>')
        if self.version_tag:
            o.append('    <bpmn:extensionElements>')
            o.append(f'      <zeebe:versionTag value="{self.version_tag}" />')
            o.append('    </bpmn:extensionElements>')
        o.append(f'    <bpmn:laneSet id="LaneSet_{self.pid}">')
        for lid, lname, _ in self.lanes:
            o.append(f'      <bpmn:lane id="{lid}" name={_attr(lname)}>')
            for el in self.elements.values():
                lane = el.lane if el.kind != "boundary" else self.elements[el.attached_to].lane
                if lane == lid:
                    o.append(f'        <bpmn:flowNodeRef>{el.id}</bpmn:flowNodeRef>')
            o.append('      </bpmn:lane>')
        o.append('    </bpmn:laneSet>')

        for el in self.elements.values():
            tag = TAGS[el.kind]
            attrs = f' id="{el.id}"'
            if el.name:
                attrs += f" name={_attr(el.name)}"
            for k, v in el.attrs.items():
                attrs += f" {k}={_attr(v)}"
            if el.id in defaults:
                attrs += f' default="{defaults[el.id]}"'
            o.append(f"    <bpmn:{tag}{attrs}>")
            if el.doc:
                o.append(f"      <bpmn:documentation>{escape(el.doc)}</bpmn:documentation>")
            if el.ext.strip():
                o.append("      <bpmn:extensionElements>")
                for line in el.ext.strip().splitlines():
                    o.append("        " + line.strip())
                o.append("      </bpmn:extensionElements>")
            for i in el.incoming:
                o.append(f"      <bpmn:incoming>{i}</bpmn:incoming>")
            for i in el.outgoing:
                o.append(f"      <bpmn:outgoing>{i}</bpmn:outgoing>")
            if el.event_def:
                o.append("      " + el.event_def)
            o.append(f"    </bpmn:{tag}>")

        for f in self.flows:
            attrs = f'id="{f.id}" sourceRef="{f.src}" targetRef="{f.tgt}"'
            if f.name:
                attrs += f" name={_attr(f.name)}"
            if f.condition:
                o.append(f"    <bpmn:sequenceFlow {attrs}>")
                o.append(f'      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">{escape(f.condition)}</bpmn:conditionExpression>')
                o.append("    </bpmn:sequenceFlow>")
            else:
                o.append(f"    <bpmn:sequenceFlow {attrs} />")

        for aid, text, *_r in self.annotations:
            o.append(f'    <bpmn:textAnnotation id="{aid}">')
            o.append(f"      <bpmn:text>{escape(text)}</bpmn:text>")
            o.append("    </bpmn:textAnnotation>")
        for aid, *_r, attach in self.annotations:
            if attach:
                o.append(f'    <bpmn:association id="Assoc_{aid}" associationDirection="None" sourceRef="{attach}" targetRef="{aid}" />')
        o.append("  </bpmn:process>")

        for eid, name, code in self.errors:
            o.append(f'  <bpmn:error id="{eid}" name={_attr(name)} errorCode="{code}" />')

        # ---- diagram interchange ----
        o.append('  <bpmndi:BPMNDiagram id="BPMNDiagram_1">')
        o.append(f'    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Collaboration_{self.pid}">')
        max_x = max(el.cx + el.size[0] / 2 for el in self.elements.values())
        for _, _, x, _, w, *_ in self.annotations:
            max_x = max(max_x, x + w)
        pool_w = max_x + 80 - self.x0
        o.append(self._shape(f"Participant_{self.pid}", self.x0, self.y0, pool_w, self.pool_h, extra=' isHorizontal="true"'))
        for lid, _, rows in self.lanes:
            o.append(self._shape(lid, self.x0 + 30, self.lane_top[lid], pool_w - 30, rows * self.row_h, extra=' isHorizontal="true"'))

        for el in self.elements.values():
            w, h = el.size
            x, y = el.cx - w / 2, el.cy - h / 2
            extra = ' isMarkerVisible="true"' if el.kind == "xor" else ""
            label = ""
            if el.kind in ("start", "end", "boundary", "xor", "and") and el.name:
                label = self._label_bounds(el)
            o.append(self._shape(el.id, x, y, w, h, extra=extra, color=el.color, label=label))

        for f in self.flows:
            label = ""
            if f.name:
                lx, ly = self._flow_label(f)
                lw = max(30, int(len(f.name) * 6.5))
                label = f'        <bpmndi:BPMNLabel>\n          <dc:Bounds x="{lx:.0f}" y="{ly:.0f}" width="{lw}" height="14" />\n        </bpmndi:BPMNLabel>\n'
            o.append(f'      <bpmndi:BPMNEdge id="{f.id}_di" bpmnElement="{f.id}">')
            for px, py in f.points:
                o.append(f'        <di:waypoint x="{px:.0f}" y="{py:.0f}" />')
            if label:
                o.append(label.rstrip("\n"))
            o.append("      </bpmndi:BPMNEdge>")

        for aid, text, x, y, w, h, attach in self.annotations:
            o.append(self._shape(aid, x, y, w, h))
            if attach:
                el = self.elements[attach]
                ax, ay = el.cx, el.cy - el.size[1] / 2 if y < el.cy else el.cy + el.size[1] / 2
                tx, ty = x + w / 2 if abs(x + w / 2 - el.cx) < w / 2 else x, (y + h if y < el.cy else y)
                o.append(f'      <bpmndi:BPMNEdge id="Assoc_{aid}_di" bpmnElement="Assoc_{aid}">')
                o.append(f'        <di:waypoint x="{ax:.0f}" y="{ay:.0f}" />')
                o.append(f'        <di:waypoint x="{ax:.0f}" y="{ty:.0f}" />')
                o.append("      </bpmndi:BPMNEdge>")

        o.append("    </bpmndi:BPMNPlane>")
        o.append("  </bpmndi:BPMNDiagram>")
        o.append("</bpmn:definitions>")
        return "\n".join(o) + "\n"

    def _label_bounds(self, el):
        w = el.label_w or 110
        chars_per_line = max(8, int(w / 6.2))
        words, lines, cur = el.name.split(), 1, 0
        for word in words:
            if cur and cur + 1 + len(word) > chars_per_line:
                lines += 1
                cur = len(word)
            else:
                cur += (1 if cur else 0) + len(word)
        h = 14 * lines
        ew, eh = el.size
        if el.label == "top":
            x, y = el.cx - w / 2, el.cy - eh / 2 - h - 6
        elif el.label == "left":
            x, y = el.cx - ew / 2 - w - 6, el.cy - h / 2
        elif el.label == "right":
            x, y = el.cx + ew / 2 + 6, el.cy - h / 2
        elif el.label == "topleft":
            x, y = el.cx - ew / 2 - w + 10, el.cy - eh / 2 - h - 2
        else:
            x, y = el.cx - w / 2, el.cy + eh / 2 + 6
        x += el.label_dx
        y += el.label_dy
        return f'        <bpmndi:BPMNLabel>\n          <dc:Bounds x="{x:.0f}" y="{y:.0f}" width="{w}" height="{h}" />\n        </bpmndi:BPMNLabel>'

    @staticmethod
    def _shape(id, x, y, w, h, extra="", color=None, label=""):
        c = ""
        if color:
            stroke, fill = PALETTE[color]
            c = (f' bioc:stroke="{stroke}" bioc:fill="{fill}" '
                 f'color:background-color="{fill}" color:border-color="{stroke}"')
        s = f'      <bpmndi:BPMNShape id="{id}_di" bpmnElement="{id}"{extra}{c}>\n'
        s += f'        <dc:Bounds x="{x:.0f}" y="{y:.0f}" width="{w:.0f}" height="{h:.0f}" />\n'
        if label:
            s += label + "\n"
        s += "      </bpmndi:BPMNShape>"
        return s


# ---- Zeebe extension snippets -------------------------------------------

def job(type_, retries=3, headers=None, inputs=None, outputs=None):
    x = f'<zeebe:taskDefinition type="{type_}" retries="{retries}" />'
    x += io(inputs, outputs)
    if headers:
        x += "\n<zeebe:taskHeaders>"
        for k, v in headers.items():
            x += f"\n<zeebe:header key={_attr(k)} value={_attr(v)} />"
        x += "\n</zeebe:taskHeaders>"
    return x


def io(inputs=None, outputs=None):
    if not inputs and not outputs:
        return ""
    x = "\n<zeebe:ioMapping>"
    for tgt, src in (inputs or {}).items():
        x += f"\n<zeebe:input source={_attr(src)} target={_attr(tgt)} />"
    for tgt, src in (outputs or {}).items():
        x += f"\n<zeebe:output source={_attr(src)} target={_attr(tgt)} />"
    x += "\n</zeebe:ioMapping>"
    return x


def rest_connector(method, url, body=None, query=None, result_expression=None,
                   error_expression=None, retries=3, retry_backoff="PT5S",
                   read_timeout=20, connect_timeout=10):
    """Camunda REST Outbound Connector (io.camunda:http-json:1) as configured by the
    'REST Outbound Connector' element template in Camunda Modeler."""
    inputs = {
        "authentication.type": "noAuth",
        "method": method,
        "url": url,
        "headers": '={"Content-Type": "application/json", "Accept": "application/json"}',
        "connectionTimeoutInSeconds": str(connect_timeout),
        "readTimeoutInSeconds": str(read_timeout),
    }
    if query:
        inputs["queryParameters"] = query
    if body:
        inputs["body"] = body
    headers = {"retryBackoff": retry_backoff}
    if result_expression:
        headers["resultExpression"] = result_expression
    if error_expression:
        headers["errorExpression"] = error_expression
    return job("io.camunda:http-json:1", retries, headers, inputs)


def rest_attrs():
    return {
        "zeebe:modelerTemplate": "io.camunda.connectors.HttpJson.v2",
        "zeebe:modelerTemplateVersion": "12",
    }


def user_task(form_id, candidate_groups, priority=50, outputs=None, inputs=None, due=None):
    x = "<zeebe:userTask />"
    x += f'\n<zeebe:formDefinition formId="{form_id}" />'
    x += f'\n<zeebe:assignmentDefinition candidateGroups="{candidate_groups}" />'
    if due:
        x += f'\n<zeebe:taskSchedule dueDate={_attr(due)} />'
    x += f'\n<zeebe:priorityDefinition priority="{priority}" />'
    x += io(inputs, outputs)
    return x


def decision(decision_id, result_variable, outputs=None):
    x = f'<zeebe:calledDecision decisionId="{decision_id}" resultVariable="{result_variable}" />'
    x += io(None, outputs)
    return x


def error_def(error_ref):
    return f'<bpmn:errorEventDefinition id="{error_ref}_def" errorRef="{error_ref}" />'


def timer_def(duration):
    return ('<bpmn:timerEventDefinition><bpmn:timeDuration xsi:type="bpmn:tFormalExpression">'
            f'{duration}</bpmn:timeDuration></bpmn:timerEventDefinition>')
