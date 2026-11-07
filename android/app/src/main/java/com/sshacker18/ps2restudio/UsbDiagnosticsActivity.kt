package com.sshacker18.ps2restudio

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only USB inventory and physical gamepad event diagnostic screen.
 * It never writes to USB endpoints or probes undocumented board test pads.
 */
class UsbDiagnosticsActivity : Activity() {
    private lateinit var usbManager: UsbManager
    private lateinit var report: TextView
    private lateinit var status: TextView
    private val log = StringBuilder()
    private var selectedDevice: UsbDevice? = null
    private var permissionConnection: UsbDeviceConnection? = null

    private val permissionAction = "com.sshacker18.ps2restudio.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                permissionAction -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = usbDeviceExtra(intent)
                    if (granted && device != null) {
                        selectedDevice = device
                        inspectDevice(device, includePermission = true)
                    } else {
                        status.text = "USB permission not granted"
                        append("USB permission denied or device detached before permission completed.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refreshInventory()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        buildUi()
        registerUsbReceiver()
        append("PS2 RE STUDIO — USB HARDWARE DIAGNOSTICS")
        append("Board profile: BM-989 (WL2039RG) VER1.5 — user-supplied visual marking; not electronically identified.")
        append("Purpose: inspect USB devices/receivers and physical controller input events.")
        append("This app cannot detect a bare PCB over USB or infer a 2.4 GHz protocol from a board marking.")
        refreshInventory()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF080B18.toInt())
        }
        val title = TextView(this).apply {
            text = "USB / GAMEPAD DIAGNOSTICS"
            textSize = 20f
            setTextColor(0xFF55D6FF.toInt())
            setPadding(0, 0, 0, pad)
        }
        status = TextView(this).apply {
            text = "Checking USB host…"
            textSize = 13f
            setTextColor(0xFFB7C9FF.toInt())
        }
        root.addView(title)
        root.addView(status)
        root.addView(actionButton("REFRESH USB DEVICES") { refreshInventory() })
        root.addView(actionButton("REQUEST PERMISSION FOR SELECTED DEVICE") {
            selectedDevice?.let(::requestUsbPermission)
                ?: append("Select a USB device from the inventory first.")
        })
        root.addView(actionButton("TEST PHYSICAL GAMEPAD INPUT") {
            append("Input test active: press physical controller buttons or move analog sticks while this screen has focus.")
            status.text = "Input test active — use physical buttons/sticks"
        })
        root.addView(actionButton("EXPORT DIAGNOSTIC REPORT") { exportReport() })
        val scroll = ScrollView(this)
        report = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFFF2F5FF.toInt())
            setPadding(0, pad, 0, pad)
            textIsSelectable = true
        }
        scroll.addView(report)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(permissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun refreshInventory() {
        val devices = usbManager.deviceList.values.sortedBy { it.deviceName }
        status.text = if (devices.isEmpty()) "No USB peripheral currently enumerated" else "${devices.size} USB device(s) enumerated"
        append("")
        append("USB INVENTORY @ ${timestamp()}")
        append("USB host feature: ${packageManager.hasSystemFeature("android.hardware.usb.host")}")
        if (devices.isEmpty()) {
            append("UsbManager.deviceList is empty.")
            append("Connect the receiver through the known-working OTG adapter, then refresh.")
            append("If Android File Manager mounts a storage device but this list is empty, capture the result and compare Android/Termux visibility.")
        }
        devices.forEachIndexed { index, device ->
            append("[$index] ${device.deviceName}")
            append("  VID:PID = %04X:%04X".format(Locale.US, device.vendorId, device.productId))
            append("  Manufacturer: ${device.manufacturerName ?: "(not exposed)"}")
            append("  Product: ${device.productName ?: "(not exposed)"}")
            append("  Serial exposed: ${if (usbManager.hasPermission(device)) safeSerial(device) else "requires permission"}")
            append("  Device class/subclass/protocol: %02X/%02X/%02X".format(Locale.US, device.deviceClass, device.deviceSubclass, device.deviceProtocol))
            append("  Interfaces: ${device.interfaceCount}; permission: ${usbManager.hasPermission(device)}")
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                append("    IF[$i] id=${intf.id} alt=${intf.alternateSetting} class=%02X subclass=%02X protocol=%02X endpoints=${intf.endpointCount}".format(Locale.US, intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol))
                for (e in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(e)
                    val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                    val type = when (ep.type) {
                        UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISO"
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                        else -> "UNKNOWN"
                    }
                    append("      EP address=0x%02X dir=%s type=%s maxPacket=${ep.maxPacketSize} interval=${ep.interval}".format(Locale.US, ep.address, dir, type))
                }
            }
        }
        render()
    }

    private fun requestUsbPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            append("Permission already granted for ${device.deviceName}.")
            inspectDevice(device, includePermission = true)
            return
        }
        selectedDevice = device
        val intent = Intent(permissionAction).setPackage(packageName)
        val pending = PendingIntent.getBroadcast(
            this, device.deviceId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, pending)
        status.text = "Waiting for Android USB permission"
        append("Requested Android USB permission for ${device.deviceName}.")
    }

    private fun inspectDevice(device: UsbDevice, includePermission: Boolean) {
        append("PERMISSIONED DEVICE INSPECTION: ${device.deviceName}")
        append("VID:PID = %04X:%04X".format(Locale.US, device.vendorId, device.productId))
        append("Permission: ${usbManager.hasPermission(device)}")
        if (!includePermission || !usbManager.hasPermission(device)) return
        // Opening a connection obtains a handle only; no interface is claimed and no endpoint is written.
        permissionConnection?.close()
        permissionConnection = usbManager.openDevice(device)
        append("UsbDeviceConnection opened: ${permissionConnection != null}")
        append("No control transfers, endpoint reads/writes, firmware commands, or pad probing are performed in this build.")
    }

    private fun safeSerial(device: UsbDevice): String = try {
        device.serialNumber ?: "(not exposed)"
    } catch (_: SecurityException) {
        "(permission required)"
    } catch (_: Exception) {
        "(unavailable)"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        if (device != null && isGamepad(device)) {
            append("KEY device=${device.name} id=${device.id} keyCode=${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} repeat=${event.repeatCount}")
            status.text = "Physical key event received: ${KeyEvent.keyCodeToString(event.keyCode)}"
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val device = event.device
        if (device != null && isGamepad(device) &&
            (event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD))) {
            val axes = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RZ, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y)
            val values = axes.joinToString { axis -> "${MotionEvent.axisToString(axis)}=${event.getAxisValue(axis)}" }
            append("MOTION device=${device.name} id=${device.id} $values")
            status.text = "Physical analog input received"
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun isGamepad(device: InputDevice): Boolean =
        device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            device.supportsSource(InputDevice.SOURCE_DPAD)

    private fun append(line: String) {
        log.append(line).append('\n')
        render()
    }

    private fun render() { if (::report.isInitialized) report.text = log.toString() }

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun exportReport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "ps2-usb-diagnostics.txt")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 401)
    }

    @Deprecated("Uses platform document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 401 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(log.toString()) }
                status.text = "Diagnostic report exported"
            } catch (e: Exception) {
                status.text = "Export failed: ${e.message}"
            }
        }
    }

    private fun usbDeviceExtra(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        permissionConnection?.close()
        super.onDestroy()
    }
}
