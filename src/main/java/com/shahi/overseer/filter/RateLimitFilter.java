package com.shahi.overseer.filter;

import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.shahi.overseer.configuration.BypassRateLimit;
import com.shahi.overseer.dto.ExceptionResponseDto;
import com.shahi.overseer.service.RateLimitingService;
import com.shahi.overseer.utility.ApiEndpointSecurityInspector;
import com.shahi.overseer.utility.AuthenticatedUserIdProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * RateLimitFilter is a custom filter registered with the spring security filter
 * chain and works in conjunction with the security configuration, as defined in
 * {@link com.shahi.overseer.configuration.SecurityConfiguration}. As per
 * established configuration, this filter is executed after evaluation of
 * {@link com.shahi.overseer.filter.JwtAuthenticationFilter}
 * 
 * This filter is responsible for enforcing rate limit on secured
 * API endpoint(s) corresponding to the user's current plan. It intercepts 
 * incoming HTTP  requests and evaluates whether the user has exhausted the 
 * limit enforced.
 * If the limit is exceeded, an error response indicating that the
 * request limit linked to the user's current plan has been exhausted
 * is returned back to the client.
 * 
 * This filter is only executed when a secure API endpoint in invoked, and is skipped
 * if the incoming request is destined to a non-secured public API endpoint.
 * 
 * Additionally, the rate limit enforcement can be bypassed for specific private
 * API endpoints by annotating the corresponding controller methods with
 * {@link com.shahi.overseer.configuration.BypassRateLimit} annotation.
 * 
 * @see com.shahi.overseer.configuration.BypassRateLimit
 * @see com.shahi.overseer.service.RateLimitingService
 * @see com.shahi.overseer.utility.ApiEndpointSecurityInspector
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

	private final ObjectMapper objectMapper;
	private final RateLimitingService rateLimitingService;
	private final RequestMappingHandlerMapping requestHandlerMapping;
	private final AuthenticatedUserIdProvider authenticatedUserIdProvider;
	private final ApiEndpointSecurityInspector apiEndpointSecurityInspector;

	private static final String RATE_LIMIT_ERROR_MESSAGE = "API request limit linked to your current plan has been exhausted.";
	private static final HttpStatus RATE_LIMIT_ERROR_STATUS = HttpStatus.TOO_MANY_REQUESTS;

	@Override
	@SneakyThrows
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
		final var unsecuredApiBeingInvoked = apiEndpointSecurityInspector.isUnsecureRequest(request);

		if (Boolean.FALSE.equals(unsecuredApiBeingInvoked) && authenticatedUserIdProvider.isAvailable()) {
			final var isRequestBypassed = isBypassed(request);

			if (Boolean.FALSE.equals(isRequestBypassed)) {
				final var userId = authenticatedUserIdProvider.getUserId();
				final var bucket = rateLimitingService.getBucket(userId);
				final var consumptionProbe = bucket.tryConsumeAndReturnRemaining(1);
				final var isConsumptionPassed = consumptionProbe.isConsumed();

				if (Boolean.FALSE.equals(isConsumptionPassed)) {
					setRateLimitErrorDetails(response, consumptionProbe);
					return;
				}

				final var remainingTokens = consumptionProbe.getRemainingTokens();
				response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
			}
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * Checks if the controller method corresponding to current request is annotated
	 * with {@link BypassRateLimit} annotation, indicating that rate limit
	 * enforcement should be bypassed.
	 *
	 * @param request HttpServletRequest representing the incoming HTTP request
	 * @return {@code true} if the request is to be bypassed, {@code false} otherwise
	 */
	@SneakyThrows
	private boolean isBypassed(HttpServletRequest request) {
		var handlerChain = requestHandlerMapping.getHandler(request);
		if (handlerChain != null && handlerChain.getHandler() instanceof HandlerMethod handlerMethod) {
			return handlerMethod.getMethod().isAnnotationPresent(BypassRateLimit.class);
		}
		return Boolean.FALSE;
	}

	/**
	 * Sets the rate limit error details in the HTTP response. This method is
	 * invoked when the user has exceeded their configured rate limit for API
	 * requests.
	 * 
	 * @param response instance of HttpServletResponse to which the rate limit error response will be set.
	 * @param consumptionProbe ConsumptionProbe object representing the rate limit consumption information.
	 */
	@SneakyThrows
	private void setRateLimitErrorDetails(HttpServletResponse response, final ConsumptionProbe consumptionProbe) {
		response.setStatus(RATE_LIMIT_ERROR_STATUS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		final var waitPeriod = TimeUnit.NANOSECONDS.toSeconds(consumptionProbe.getNanosToWaitForRefill());
		response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitPeriod));

		final var errorResponse = prepareErrorResponseBody();
		response.getWriter().write(errorResponse);
	}

	/**
	 * Returns a JSON representation of the rate limit exhaustion error response
	 * body.
	 */
	@SneakyThrows
	private String prepareErrorResponseBody() {
		final var exceptionResponse = new ExceptionResponseDto<String>();
		exceptionResponse.setStatus(RATE_LIMIT_ERROR_STATUS.toString());
		exceptionResponse.setDescription(RATE_LIMIT_ERROR_MESSAGE);
		return objectMapper.writeValueAsString(exceptionResponse);
	}

}