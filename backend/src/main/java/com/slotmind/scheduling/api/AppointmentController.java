package com.slotmind.scheduling.api;

import com.slotmind.scheduling.domain.AppointmentStatus;
import com.slotmind.scheduling.service.AppointmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Thin by design: validate, delegate, map status. No business logic, no try/catch. */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

  private final AppointmentService service;

  @GetMapping
  public List<AppointmentResponse> list(@RequestParam(required = false) AppointmentStatus status) {
    return service.list(status);
  }

  @GetMapping("/{id}")
  public AppointmentResponse get(@PathVariable String id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request) {
    return service.book(request);
  }
}
