package fr.emse.ismin.reservation.controllers;

import fr.emse.ismin.reservation.dtos.AvailableRoomResponse;
import fr.emse.ismin.reservation.dtos.CreateRoomRequest;
import fr.emse.ismin.reservation.dtos.EquipmentRequest;
import fr.emse.ismin.reservation.dtos.ReplaceRoomEquipmentRequest;
import fr.emse.ismin.reservation.dtos.RoomResponse;
import fr.emse.ismin.reservation.dtos.UpdateRoomRequest;
import fr.emse.ismin.reservation.dtos.UpdateRoomStatusRequest;
import fr.emse.ismin.reservation.models.Room;
import fr.emse.ismin.reservation.services.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * HTTP endpoints of the rooms ({@code /api/rooms}).
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * Creates a room.
     *
     * @param request name, location, capacity and equipment
     * @return 201 with the created room and its URI in the {@code Location} header
     */
    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{roomId}")
                .buildAndExpand(room.getId())
                .toUri();
        return ResponseEntity.created(location).body(RoomResponse.from(room));
    }

    /**
     * @return all rooms with their status and equipment, sorted by name
     */
    @GetMapping
    public List<RoomResponse> findAll() {
        return roomService.findAll().stream()
                .map(RoomResponse::from)
                .toList();
    }

    /**
     * Searches the rooms that can host a reservation over {@code [start, end[}.
     * Dates must follow ISO 8601 with an offset, for example
     * {@code 2026-10-15T14:00:00+02:00} (the {@code +} must be URL-encoded as {@code %2B}).
     *
     * @param start     start of the period
     * @param end       end of the period
     * @param capacity  number of people to host
     * @param equipment codes of the requested equipment, separated by commas
     * @return the compatible rooms, sorted by unused seats, name and id
     */
    @GetMapping("/available")
    public List<AvailableRoomResponse> findAvailable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @RequestParam @Min(value = 1, message = "doit être strictement positive") Integer capacity,
            @RequestParam(required = false)
            List<@Pattern(regexp = EquipmentRequest.CODE_PATTERN, message = EquipmentRequest.CODE_MESSAGE) String>
                    equipment) {
        return roomService.findAvailable(start.toInstant(), end.toInstant(), capacity, equipment).stream()
                .map(room -> AvailableRoomResponse.from(room, capacity))
                .toList();
    }

    /**
     * @param roomId id of the room
     * @return the room with its status and equipment
     */
    @GetMapping("/{roomId}")
    public RoomResponse findById(@PathVariable Long roomId) {
        return RoomResponse.from(roomService.findById(roomId));
    }

    /**
     * Replaces the name, the location and the capacity of a room.
     *
     * @param roomId  id of the room
     * @param request new name, location and capacity
     * @return the updated room
     */
    @PutMapping("/{roomId}")
    public RoomResponse update(@PathVariable Long roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return RoomResponse.from(roomService.update(roomId, request));
    }

    /**
     * Puts a room in {@code AVAILABLE} or {@code MAINTENANCE}.
     *
     * @param roomId  id of the room
     * @param request the new status
     * @return the updated room
     */
    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(@PathVariable Long roomId, @Valid @RequestBody UpdateRoomStatusRequest request) {
        return RoomResponse.from(roomService.updateStatus(roomId, request.status()));
    }

    /**
     * Replaces all the equipment of a room.
     *
     * @param roomId  id of the room
     * @param request codes of the new equipment
     * @return the updated room
     */
    @PutMapping("/{roomId}/equipment")
    public RoomResponse replaceEquipment(@PathVariable Long roomId,
                                         @Valid @RequestBody ReplaceRoomEquipmentRequest request) {
        return RoomResponse.from(roomService.replaceEquipment(roomId, request.equipmentCodes()));
    }
}
