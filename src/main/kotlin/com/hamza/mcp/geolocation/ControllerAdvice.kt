package com.hamza.mcp.geolocation

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.server.ServerWebExchange
import java.time.Instant

@RestControllerAdvice
class ControllerAdvice {
    @ExceptionHandler(HandlerMethodValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(
        ex: HandlerMethodValidationException,
        exchange: ServerWebExchange,
    ) = mapOf(
        "timestamp" to Instant.now(),
        "path" to exchange.request.path.value(),
        "status" to HttpStatus.BAD_REQUEST.value(),
        "error" to HttpStatus.BAD_REQUEST.reasonPhrase,
        "requestId" to exchange.request.id,
        "message" to (ex.allErrors.firstOrNull()?.defaultMessage ?: "Validation failed"),
    )
}
