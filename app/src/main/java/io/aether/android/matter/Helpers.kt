// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

import io.aether.android.R

/** The root endpoint ID for Matter devices. */
val ROOT_ENDPOINT_ID: EndpointId = EndpointId(0u)

/** The wildcard attribute ID for Matter devices. */
val WILDCARD_ATTRIBUTE_ID: AttributeId = AttributeId(0xFFFFFFFFu)

/** Checks if the data type is numeric (e.g. should use numeric keyboard). */
fun DataType.isNumeric(): Boolean =
    when (this) {
      DataType.CLUSTER_ID,
      DataType.ENDPOINT_ID,
      DataType.ENUM16,
      DataType.ENUM8,
      DataType.EPOCH_MICROSECONDS,
      DataType.EPOCH_SECONDS,
      DataType.FABRIC_INDEX,
      DataType.GROUP_ID,
      DataType.INT16,
      DataType.INT32,
      DataType.INT64,
      DataType.INT8,
      DataType.MAP16,
      DataType.MAP32,
      DataType.MAP8,
      DataType.MESSAGE_ID,
      DataType.NODE_ID,
      DataType.SUBJECT_ID,
      DataType.TLS_ENDPOINT_ID,
      DataType.TLSCAID,
      DataType.TLSCCDID,
      DataType.U_INT16,
      DataType.U_INT24,
      DataType.U_INT32,
      DataType.U_INT64,
      DataType.U_INT8,
      DataType.VENDOR_ID -> true
      else -> false
    }

/** Returns the drawable resource ID for the device type icon. */
fun DeviceTypeId.getDeviceTypeIconId(): Int {
  return when (this) {
    Devices.DoorLock.ID -> R.drawable.matter_device_type_000a
    Devices.DoorLockController.ID -> R.drawable.matter_device_type_000b
    Devices.Aggregator.ID -> R.drawable.matter_device_type_000e
    Devices.GenericSwitch.ID -> R.drawable.matter_device_type_000f
    Devices.PowerSource.ID -> R.drawable.matter_device_type_0011
    Devices.OTARequestor.ID -> R.drawable.matter_device_type_0012
    Devices.BridgedNode.ID -> R.drawable.matter_device_type_0013
    Devices.OTAProvider.ID -> R.drawable.matter_device_type_0014
    Devices.ContactSensor.ID -> R.drawable.matter_device_type_0015
    Devices.RootNode.ID -> R.drawable.matter_device_type_0016
    Devices.SolarPower.ID -> R.drawable.matter_device_type_0017
    Devices.BatteryStorage.ID -> R.drawable.matter_device_type_0018
    Devices.SecondaryNetworkInterface.ID -> R.drawable.matter_device_type_0019
    Devices.Speaker.ID -> R.drawable.matter_device_type_0022
    Devices.CastingVideoPlayer.ID -> R.drawable.matter_device_type_0023
    Devices.ContentApp.ID -> R.drawable.matter_device_type_0024
    Devices.ModeSelect.ID -> R.drawable.matter_device_type_0027
    Devices.BasicVideoPlayer.ID -> R.drawable.matter_device_type_0028
    Devices.CastingVideoClient.ID -> R.drawable.matter_device_type_0029
    Devices.VideoRemoteControl.ID -> R.drawable.matter_device_type_002a
    Devices.Fan.ID -> R.drawable.matter_device_type_002b
    Devices.AirQualitySensor.ID -> R.drawable.matter_device_type_002c
    Devices.AirPurifier.ID -> R.drawable.matter_device_type_002d
    Devices.IrrigationSystem.ID -> R.drawable.matter_device_type_0040
    Devices.WaterFreezeDetector.ID -> R.drawable.matter_device_type_0041
    Devices.WaterValve.ID -> R.drawable.matter_device_type_0042
    Devices.WaterLeakDetector.ID -> R.drawable.matter_device_type_0043
    Devices.RainSensor.ID -> R.drawable.matter_device_type_0044
    Devices.SoilSensor.ID -> R.drawable.matter_device_type_0045
    Devices.Refrigerator.ID -> R.drawable.matter_device_type_0070
    Devices.TemperatureControlledCabinet.ID -> R.drawable.matter_device_type_0071
    Devices.RoomAirConditioner.ID -> R.drawable.matter_device_type_0072
    Devices.LaundryWasher.ID -> R.drawable.matter_device_type_0073
    Devices.RoboticVacuumCleaner.ID -> R.drawable.matter_device_type_0074
    Devices.Dishwasher.ID -> R.drawable.matter_device_type_0075
    Devices.SmokeCOAlarm.ID -> R.drawable.matter_device_type_0076
    Devices.CookSurface.ID -> R.drawable.matter_device_type_0077
    Devices.Cooktop.ID -> R.drawable.matter_device_type_0078
    Devices.MicrowaveOven.ID -> R.drawable.matter_device_type_0079
    Devices.ExtractorHood.ID -> R.drawable.matter_device_type_007a
    Devices.Oven.ID -> R.drawable.matter_device_type_007b
    Devices.LaundryDryer.ID -> R.drawable.matter_device_type_007c
    Devices.NetworkInfrastructureManager.ID -> R.drawable.matter_device_type_0090
    Devices.ThreadBorderRouter.ID -> R.drawable.matter_device_type_0091
    Devices.OnOffLight.ID -> R.drawable.matter_device_type_0100
    Devices.DimmableLight.ID -> R.drawable.matter_device_type_0101
    Devices.OnOffLightSwitch.ID -> R.drawable.matter_device_type_0103
    Devices.DimmerSwitch.ID -> R.drawable.matter_device_type_0104
    Devices.ColorDimmerSwitch.ID -> R.drawable.matter_device_type_0105
    Devices.LightSensor.ID -> R.drawable.matter_device_type_0106
    Devices.OccupancySensor.ID -> R.drawable.matter_device_type_0107
    Devices.OnOffPluginUnit.ID -> R.drawable.matter_device_type_010a
    Devices.DimmablePlugInUnit.ID -> R.drawable.matter_device_type_010b
    Devices.ColorTemperatureLight.ID -> R.drawable.matter_device_type_010c
    Devices.ExtendedColorLight.ID -> R.drawable.matter_device_type_010d
    Devices.MountedOnOffControl.ID -> R.drawable.matter_device_type_010f
    Devices.MountedDimmableLoadControl.ID -> R.drawable.matter_device_type_0110
    Devices.JointFabricAdministrator.ID -> R.drawable.matter_device_type_0130
    Devices.Intercom.ID -> R.drawable.matter_device_type_0140
    Devices.AudioDoorbell.ID -> R.drawable.matter_device_type_0141
    Devices.Camera.ID -> R.drawable.matter_device_type_0142
    Devices.VideoDoorbell.ID -> R.drawable.matter_device_type_0143
    Devices.FloodlightCamera.ID -> R.drawable.matter_device_type_0144
    Devices.SnapshotCamera.ID -> R.drawable.matter_device_type_0145
    Devices.Chime.ID -> R.drawable.matter_device_type_0146
    Devices.CameraController.ID -> R.drawable.matter_device_type_0147
    Devices.Doorbell.ID -> R.drawable.matter_device_type_0148
    Devices.WindowCovering.ID -> R.drawable.matter_device_type_0202
    Devices.WindowCoveringController.ID -> R.drawable.matter_device_type_0203
    Devices.Closure.ID -> R.drawable.matter_device_type_0230
    Devices.ClosurePanel.ID -> R.drawable.matter_device_type_0231
    Devices.ClosureController.ID -> R.drawable.matter_device_type_023e
    Devices.HeatingCoolingUnit.ID -> R.drawable.matter_device_type_0300
    Devices.Thermostat.ID -> R.drawable.matter_device_type_0301
    Devices.TemperatureSensor.ID -> R.drawable.matter_device_type_0302
    Devices.Pump.ID -> R.drawable.matter_device_type_0303
    Devices.PumpController.ID -> R.drawable.matter_device_type_0304
    Devices.PressureSensor.ID -> R.drawable.matter_device_type_0305
    Devices.FlowSensor.ID -> R.drawable.matter_device_type_0306
    Devices.HumiditySensor.ID -> R.drawable.matter_device_type_0307
    Devices.HeatPump.ID -> R.drawable.matter_device_type_0309
    Devices.ThermostatController.ID -> R.drawable.matter_device_type_030a
    Devices.EnergyEVSE.ID -> R.drawable.matter_device_type_050c
    Devices.DeviceEnergyManagement.ID -> R.drawable.matter_device_type_050d
    Devices.WaterHeater.ID -> R.drawable.matter_device_type_050f
    Devices.ElectricalSensor.ID -> R.drawable.matter_device_type_0510
    Devices.ElectricalUtilityMeter.ID -> R.drawable.matter_device_type_0511
    Devices.MeterReferencePoint.ID -> R.drawable.matter_device_type_0512
    Devices.ElectricalEnergyTariff.ID -> R.drawable.matter_device_type_0513
    Devices.ElectricalMeter.ID -> R.drawable.matter_device_type_0514
    Devices.ControlBridge.ID -> R.drawable.matter_device_type_0840
    Devices.OnOffSensor.ID -> R.drawable.matter_device_type_0850
    else -> R.drawable.matter_device_type_unknown
  }
}
