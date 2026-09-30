package com.example.retirement_planner;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ProjectionController.class, PlanningController.class, ScenarioController.class, AnalysisController.class, ChatController.class})
public class ProjectionExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidInput(IllegalArgumentException exception) {
        return problem(exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail unreadableRequest() {
        return problem("Send a JSON object with whole-number ages and numeric savings, contributions, and rates.");
    }

    private static ProblemDetail problem(String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid projection inputs");
        return problem;
    }
}
