package edu.pbj2.lab1.validation;

/**
 * A process variable has the wrong data type (e.g. {@code monthlyIncome = "five thousand"}).
 * Retrying cannot fix this, so the worker raises an incident immediately; an operator corrects
 * the variable in Operate and retries the job.
 */
public class MalformedVariableException extends RuntimeException {

  public MalformedVariableException(String message) {
    super(message);
  }
}
