package com.campusflow.common.exception;

import com.campusflow.common.api.ApiResponse;
import com.campusflow.common.api.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @Nullable ResponseEntity<Object> handleBusinessException(BusinessException exception, WebRequest request) {
        var code = exception.code();
        return handleExceptionInternal(exception, ApiResponse.failure(code), HttpHeaders.EMPTY, code.status(), request);
    }

    @ExceptionHandler(Exception.class)
    @Nullable ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, HttpHeaders.EMPTY, ErrorCode.INTERNAL_ERROR.status(), request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    @Nullable ResponseEntity<Object> handleConcurrentChange(ConcurrencyFailureException exception,
                                                            WebRequest request) {
        var code = ErrorCode.CONFLICT;
        return handleExceptionInternal(exception, ApiResponse.failure(code), HttpHeaders.EMPTY, code.status(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var issues = bindingIssues(exception.getBindingResult()).toList();
        return handleExceptionInternal(exception, ApiResponse.failure(ErrorCode.VALIDATION_ERROR, issues),
                headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return handleExceptionInternal(exception, null, headers, status, request);
        }

        var issues = exception.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::parameterIssues)
                .toList();
        return handleExceptionInternal(exception, ApiResponse.failure(ErrorCode.VALIDATION_ERROR, issues),
                headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            logServerFailure(exception);
        }

        var response = body instanceof ApiResponse<?> ? body : ApiResponse.failure(errorCode(exception, status));
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.addAll(headers);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, response, jsonHeaders, status, request);
    }

    private static void logServerFailure(Throwable exception) {
        // 只记录类型和栈帧，保留排查线索并避免记录异常消息中的请求数据。
        var trace = new ArrayList<String>();
        for (Throwable cause = exception; cause != null && trace.size() < 8; cause = cause.getCause()) {
            var frames = Arrays.stream(cause.getStackTrace()).limit(12).toList();
            trace.add(cause.getClass().getName() + " " + frames);
        }
        log.error("处理请求失败，异常链={}", trace);
    }

    private static ErrorCode errorCode(Exception exception, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.INVALID_REQUEST;
        };
    }

    private static Stream<ValidationIssue> bindingIssues(Errors errors) {
        return errors.getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError ? publicField(fieldError.getField()) : "request";
                    return new ValidationIssue(field, constraintMessage(error));
                });
    }

    private static Stream<ValidationIssue> parameterIssues(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors) {
            return bindingIssues(errors);
        }

        var parameter = result.getMethodParameter();
        String name = parameter.getParameterName();
        String field = name != null ? name : "arg" + parameter.getParameterIndex();
        return result.getResolvableErrors().stream()
                .map(error -> new ValidationIssue(field, constraintMessage(error)));
    }

    private static String publicField(String field) {
        // 容器路径的方括号中可能含有客户端提供的映射键。
        int index = field.indexOf('[');
        return index < 0 ? field : field.substring(0, index);
    }

    private static String constraintMessage(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
        return switch (constraint) {
            case "NotNull" -> "不能为空";
            case "NotBlank" -> "不得为空白";
            case "Size" -> "长度或数量超出允许范围";
            case "Email" -> "邮箱格式不正确";
            default -> "参数无效";
        };
    }

    private record ValidationIssue(String field, String message) {
    }
}
