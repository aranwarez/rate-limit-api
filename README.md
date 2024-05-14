# Spring Boot Rate Limiting API with Bucket Algorithm

This project demonstrates how to implement rate limiting for APIs using Spring Boot and the Bucket Algorithm.

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)

## Introduction

Rate limiting is a crucial aspect of managing APIs to prevent abuse, maintain quality of service, and ensure fair usage. This project provides a simple implementation of rate limiting using Spring Boot, leveraging the Bucket Algorithm.

## Features

- Implements rate limiting for API endpoints.
- Uses the Bucket Algorithm for efficient rate limiting.
- Easy to integrate into Spring Boot applications.
- Customizable configuration options.

## Prerequisites

Make sure you have the following installed before setting up this project:

- Java 8 or later
- Maven

## Installation

To use this project, follow these steps:

1. Clone the repository: `git clone <repository-url>`
2. Navigate to the project directory: `cd rate-limit-api`
3. Build the project: `mvn clean install`

---
### Local Setup
The below given commands can be executed in the project's base directory to build an image and start required container(s). Docker compose will initiate a MySQL and Redis container as well, with the backend swagger-ui accessible at `http://localhost:8080/swagger-ui.html`
```bash
sudo docker-compose build
```
```bash
sudo docker-compose up -d
```

---

## Configuration

You can configure the rate limiting settings in the `application.properties` file:

```properties
# Rate limiting configuration
rate.limit.enabled=true
rate.limit.requests=100
rate.limit.duration=60
```
### Bypass Rate limit Enforcement
Bypassing rate limit enforcement for specific private API endpoints can be achieved by annotating the corresponding controller method(s) with the `@BypassRateLimit` annotation. When applied, requests to that method are not subjected to rate limiting by the [RateLimitFilter.java](https://github.com/hardikSinghBehl/rate-limiting-api-spring-boot/blob/main/src/main/java/com/behl/overseer/filter/RateLimitFilter.java) and allowed regardless of the user's current rate limit plan.

The below private API endpoint to update a user's current plan is annotated with `@BypassRateLimit` to ensure requests to update to a new plan are not restricted by the user's rate limit.

```java
@BypassRateLimit
@PutMapping(value = "/api/v1/plan")
public ResponseEntity<HttpStatus> update(@RequestBody PlanUpdationRequest planUpdationRequest) {
    planService.update(planUpdationRequest);
    return ResponseEntity.status(HttpStatus.OK).build();
}
```

### Create your Own Custom Plans
* During the initial launch of the application, Preconfiged are created and populated with data using Flyway migration scripts. You can create your custom plans by adding more plans. In database table.

    | Name          | Limit per Hour |
    |---------------|----------------|
    | FREE          | 20             |
    | BUSINESS      | 40             |
    | PROFESSIONAL  | 100            |

  * When the rate limit assigned gets exhausted for a user, the below API response is sent back to the client  
    ```
    {
      "Status": "429 TOO_MANY_REQUESTS",
      "Description": "API request limit linked to your current plan has been exhausted."
    }

    ```
### Rate Limit Headers
After evaluation of incoming HTTP requests against the user's rate limit, the RateLimitFilter.java includes additional HTTP headers in the response to provide more information. These headers are useful for client applications to understand the rate limit status and adjust their behavior accordingly to handle rate limit violations gracefully.

| Header Name                      | Description                                                                                                           |
|--------------------------------  |---------------------------------------------------------------------------------------------------------------------  |
| X-Rate-Limit-Remaining           | Indicates the number of remaining tokens available in the user's rate limit bucket after processing the request.      |
| X-Rate-Limit-Retry-After-Seconds | Specifies the wait period in seconds before the user can retry making requests, in case they exceed their rate limit. |


