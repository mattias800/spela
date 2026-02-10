# Manual QA Checklist: Physical Gamepad/Controller Support (Bug #4)

This bug cannot be tested via Maestro E2E because Maestro uses the Android
accessibility service for input, which cannot simulate physical gamepad events.
The Activity-level handlers (`onKeyDown`/`onKeyUp`/`onGenericMotionEvent`)
require `InputDevice.SOURCE_GAMEPAD` source flag, which ADB key injection
also cannot provide.

## Prerequisites

- Android device or emulator with the Spela app installed
- A physical Bluetooth or USB gamepad (Xbox, PlayStation, or generic HID)
- A running Spela server with seeded data and NES ROMs

## Test Steps

### 1. Connect Controller
- [ ] Pair a Bluetooth gamepad or plug in a USB gamepad
- [ ] Verify Android recognizes the controller (Settings > Connected devices)

### 2. Navigate to Game
- [ ] Open Spela app, connect to server, login
- [ ] Navigate to a NES game (e.g., Castlevania)
- [ ] Download and tap Play

### 3. Verify D-Pad Input
- [ ] Press D-Pad Up — character/cursor moves up
- [ ] Press D-Pad Down — character/cursor moves down
- [ ] Press D-Pad Left — character/cursor moves left
- [ ] Press D-Pad Right — character/cursor moves right

### 4. Verify Face Buttons
- [ ] Press A/Cross button — mapped to libretro B (confirm/jump)
- [ ] Press B/Circle button — mapped to libretro A (back/attack)
- [ ] Press X/Square button — mapped to libretro Y
- [ ] Press Y/Triangle button — mapped to libretro X

### 5. Verify Shoulder/Trigger Buttons
- [ ] Press L1/LB — mapped to libretro L
- [ ] Press R1/RB — mapped to libretro R

### 6. Verify Start/Select
- [ ] Press Start button — mapped to libretro Start
- [ ] Press Select/Back button — mapped to libretro Select

### 7. Verify Analog Sticks (if applicable)
- [ ] Move left stick — analog input with dead zone (~0.1)
- [ ] Move right stick — analog input with dead zone (~0.1)
- [ ] Verify dead zone: small movements near center produce no input

### 8. Verify Button Release
- [ ] Press and hold a button — action continues
- [ ] Release the button — action stops immediately
- [ ] Rapid press/release — no stuck inputs

### 9. Verify No Interference with Touch Controls
- [ ] While gamepad is connected, touch controls still work
- [ ] Gamepad and touch can be used simultaneously without conflicts

## Expected Results

All gamepad buttons should map correctly to libretro input and produce
the expected in-game actions. No button should be unresponsive or mapped
to the wrong action. Analog sticks should have smooth response with
proper dead zone filtering.
