package com.sshacker18.ps2restudio

import android.app.Activity
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Android input bridge for physical USB/Bluetooth HID controllers.
 *
 * Android's input framework handles USB HID enumeration and permissions for
 * standard gamepads. This class deliberately does not create touch controls.
 * A future emulator adapter should consume the callbacks and translate them
 * to the selected PS2 core's controller API.
 */
abstract class GamepadInputActivity : Activity(), InputManager.InputDeviceListener {
    data class PhysicalGamepad(
        val id: Int,
        val name: String,
        val sources: Int,
        val isExternal: Boolean
    )

    protected val connectedPhysicalGamepads: List<PhysicalGamepad>
        get() = InputDevice.getDeviceIds().mapNotNull { id ->
            InputDevice.getDevice(id)?.takeIf(::isGamepad)?.let {
                PhysicalGamepad(it.id, it.name ?: "Unknown controller", it.sources, it.isExternal)
            }
        }

    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager.registerInputDeviceListener(this, null)
        onPhysicalGamepadsChanged(connectedPhysicalGamepads)
    }

    override fun onDestroy() {
        inputManager.unregisterInputDeviceListener(this)
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadKey(event)) {
            if (onPhysicalGamepadKeyEvent(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE && isGamepadMotion(event)) {
            if (onPhysicalGamepadMotionEvent(event)) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onInputDeviceAdded(deviceId: Int) = notifyGamepadsChanged()
    override fun onInputDeviceRemoved(deviceId: Int) = notifyGamepadsChanged()
    override fun onInputDeviceChanged(deviceId: Int) = notifyGamepadsChanged()

    private fun notifyGamepadsChanged() = onPhysicalGamepadsChanged(connectedPhysicalGamepads)

    protected open fun onPhysicalGamepadsChanged(devices: List<PhysicalGamepad>) = Unit

    /** Return true when the emulator has consumed this key event. */
    protected open fun onPhysicalGamepadKeyEvent(event: KeyEvent): Boolean = false

    /** Return true when the emulator has consumed this joystick-axis event. */
    protected open fun onPhysicalGamepadMotionEvent(event: MotionEvent): Boolean = false

    private fun isGamepad(device: InputDevice): Boolean =
        device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            device.supportsSource(InputDevice.SOURCE_DPAD)

    private fun isGamepadKey(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        return isGamepad(device) &&
            (event.isGamepadButton || event.keyCode in DPAD_KEYS || event.keyCode in SYSTEM_GAMEPAD_KEYS)
    }

    private fun isGamepadMotion(event: MotionEvent): Boolean {
        val device = event.device ?: return false
        return isGamepad(device) &&
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                event.isFromSource(InputDevice.SOURCE_GAMEPAD))
    }

    companion object {
        private val DPAD_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER
        )
        private val SYSTEM_GAMEPAD_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE
        )

        /** Common analog axes exposed by Android USB/Bluetooth HID drivers. */
        val STANDARD_STICK_AXES = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RY
        )
    }
}
