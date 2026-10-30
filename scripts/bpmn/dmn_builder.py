"""
Minimal DMN 1.3 decision-table writer (Camunda 8 flavour) plus a Markdown renderer,
so the deployed table and the rule documentation in each REPORT.md never drift apart.
"""
from xml.sax.saxutils import escape, quoteattr


class DecisionTable:
    def __init__(self, decision_id, name, hit_policy="FIRST", doc=None):
        self.id, self.name, self.hit_policy, self.doc = decision_id, name, hit_policy, doc
        self.inputs, self.outputs, self.rules = [], [], []

    def input(self, key, label, expr, type_ref):
        self.inputs.append((key, label, expr, type_ref))

    def output(self, key, label, name, type_ref, allowed=None):
        self.outputs.append((key, label, name, type_ref, allowed))

    def rule(self, description, inputs, outputs):
        """inputs/outputs: lists of FEEL unary tests / expressions; '-' = any."""
        assert len(inputs) == len(self.inputs) and len(outputs) == len(self.outputs)
        self.rules.append((description, inputs, outputs))

    def to_xml(self):
        o = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" '
             'xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" '
             'xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" '
             'xmlns:modeler="http://camunda.org/schema/modeler/1.0" '
             f'id="Definitions_{self.id}" name={quoteattr(self.name)} '
             'namespace="http://camunda.org/schema/1.0/dmn" exporter="Camunda Modeler" exporterVersion="5.49.0" '
             'modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.8.0">',
             f'  <decision id="{self.id}" name={quoteattr(self.name)}>']
        if self.doc:
            o.append(f"    <description>{escape(self.doc)}</description>")
        o.append(f'    <decisionTable id="DecisionTable_{self.id}" hitPolicy="{self.hit_policy}">')
        for key, label, expr, type_ref in self.inputs:
            o.append(f'      <input id="Input_{key}" label={quoteattr(label)}>')
            o.append(f'        <inputExpression id="InputExpression_{key}" typeRef="{type_ref}">')
            o.append(f"          <text>{escape(expr)}</text>")
            o.append("        </inputExpression>")
            o.append("      </input>")
        for key, label, name, type_ref, allowed in self.outputs:
            o.append(f'      <output id="Output_{key}" label={quoteattr(label)} name="{name}" typeRef="{type_ref}">')
            if allowed:
                o.append(f'        <outputValues id="OutputValues_{key}">')
                o.append(f"          <text>{escape(allowed)}</text>")
                o.append("        </outputValues>")
            o.append("      </output>")
        for i, (desc, ins, outs) in enumerate(self.rules, 1):
            o.append(f'      <rule id="Rule_{self.id.replace("-", "_")}_{i}">')
            o.append(f"        <description>{escape(desc)}</description>")
            for j, v in enumerate(ins):
                o.append(f'        <inputEntry id="In_{i}_{j}">')
                o.append(f"          <text>{escape(v)}</text>")
                o.append("        </inputEntry>")
            for j, v in enumerate(outs):
                o.append(f'        <outputEntry id="Out_{i}_{j}">')
                o.append(f"          <text>{escape(v)}</text>")
                o.append("        </outputEntry>")
            o.append("      </rule>")
        o.append("    </decisionTable>")
        o.append("  </decision>")
        o.append('  <dmndi:DMNDI>')
        o.append('    <dmndi:DMNDiagram id="DMNDiagram_1">')
        o.append(f'      <dmndi:DMNShape id="DMNShape_{self.id}" dmnElementRef="{self.id}">')
        o.append('        <dc:Bounds height="80" width="180" x="160" y="100" />')
        o.append("      </dmndi:DMNShape>")
        o.append("    </dmndi:DMNDiagram>")
        o.append("  </dmndi:DMNDI>")
        o.append("</definitions>")
        return "\n".join(o) + "\n"

    def to_markdown(self):
        head = ["#"] + [f"{label} (`{expr}`)" for _, label, expr, _ in self.inputs] + \
               [f"→ {label} (`{name}`)" for _, label, name, _, _ in self.outputs] + ["Rule description"]
        lines = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
        for i, (desc, ins, outs) in enumerate(self.rules, 1):
            cells = [str(i)] + [("–" if v == "-" else f"`{v}`") for v in ins] + [f"`{v}`" for v in outs] + [desc]
            lines.append("| " + " | ".join(c.replace("|", "\\|") for c in cells) + " |")
        return "\n".join(lines) + "\n"
