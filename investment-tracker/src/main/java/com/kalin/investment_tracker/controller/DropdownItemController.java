package com.kalin.investment_tracker.controller;
import com.kalin.investment_tracker.dto.DropdownItemDto;
import com.kalin.investment_tracker.dto.DropdownItemResponseDto;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.service.DropdownItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin("http://localhost:3000")
public class DropdownItemController {

    private final DropdownItemService dropdownItemService;
    private final JwtService jwtService;

    public DropdownItemController(DropdownItemService dropdownItemService, JwtService jwtService) {
        this.dropdownItemService = dropdownItemService;
        this.jwtService = jwtService;
    }

    @PostMapping("/dropdownItem")
    public ResponseEntity<DropdownItemResponseDto> addDropdownItem(
            @Valid @RequestBody DropdownItemDto dto,
            @RequestHeader("Authorization") String token
    ) {
        try {
            String jwt = token.substring(7); // Remove "Bearer " prefix
            String userIdStr = jwtService.extractUserId(jwt);
            Integer userId = Integer.parseInt(userIdStr);

            DropdownItemResponseDto responseDto = dropdownItemService.addDropdownItem(dto, userId);
            return ResponseEntity.ok(responseDto);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Item with this name already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/dropdownItems")
    public ResponseEntity<Page<DropdownItemResponseDto>> getAllDropdownItems(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            String jwt = token.substring(7); // Remove "Bearer " prefix
            String userIdStr = jwtService.extractUserId(jwt);
            Integer userId = Integer.parseInt(userIdStr);

            Page<DropdownItemResponseDto> responseDtoPage = dropdownItemService.getAllDropdownItems(userId, page, size);
            return ResponseEntity.ok(responseDtoPage);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/dropdownItems/search")
    public ResponseEntity<Page<DropdownItemResponseDto>> searchDropdownItems(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            String jwt = token.substring(7);
            String userIdStr = jwtService.extractUserId(jwt);
            Integer userId = Integer.parseInt(userIdStr);

            Page<DropdownItemResponseDto> responseDtoPage = dropdownItemService.getFilteredDropdownItems(userId, searchTerm, page, size);
            return ResponseEntity.ok(responseDtoPage);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/dropdownItems/{id}")
    public ResponseEntity<Void> deleteDropdownItem(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        try {
            String jwt = token.substring(7);
            String userIdStr = jwtService.extractUserId(jwt);
            Integer userId = Integer.parseInt(userIdStr);

            dropdownItemService.deleteById(id, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors()
                .forEach(error -> {
                    String fieldName = ((FieldError) error).getField();
                    String errorMessage = error.getDefaultMessage();
                    errors.put(fieldName, errorMessage);
                });
        return ResponseEntity.badRequest().body(errors);
    }
}


