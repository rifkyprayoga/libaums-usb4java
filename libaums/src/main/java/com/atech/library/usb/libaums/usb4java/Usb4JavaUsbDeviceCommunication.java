package com.atech.library.usb.libaums.usb4java;

import com.atech.library.usb.libaums.data.LibAumsException;
import com.atech.library.usb.libaums.data.UsbMassStorageDeviceConfig;
import com.atech.library.usb.libaums.UsbMassStorageLibrary;
import com.github.mjdev.libaums.UsbCommunication;
import lombok.extern.slf4j.Slf4j;
import org.usb4java.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Created by andy on 11.04.2024.
 */
@Slf4j
public class Usb4JavaUsbDeviceCommunication implements UsbCommunication {

    private static final int TRANSFER_TIMEOUT = 21000;
    DeviceHandle deviceHandle;
    UsbMassStorageDeviceConfig deviceConfig;

    public Usb4JavaUsbDeviceCommunication(UsbMassStorageDeviceConfig usbMassStorageDeviceConfig) {
        this.deviceConfig = usbMassStorageDeviceConfig;
    }

    public void openDevice() throws LibAumsException {
        log.info("openDevice {}", deviceConfig.getReadableDeviceId());
        Context context = UsbMassStorageLibrary.initLibrary();

        // Open device
        DeviceHandle handle = LibUsb.openDeviceWithVidPid(context, deviceConfig.getVendorId(),
                deviceConfig.getProductId());
        if (handle == null) {
            log.error("Device {} not found, or could not be opened.", deviceConfig.getReadableDeviceId());
            System.exit(1);
        }

        this.deviceHandle = handle;

        // Claim interface
        int result = LibUsb.claimInterface(handle, deviceConfig.getInterfaceNumber());
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to claim interface", result);
        }

        log.info("Device {} opened and interafce {} claimed.", deviceConfig.getReadableDeviceId(), deviceConfig.getInterfaceNumber());
    }


    @Override
    public int bulkOutTransfer(byte[] data, int length) throws LibAumsException {
        // TODO implement Usb4JavaUsbDeviceCommunication

        log.info("BulkOutTransfer (data={},length={})", data, length);

        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
        buffer.put(data);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(deviceHandle,  deviceConfig.getOutEndpointAddress(), buffer,
                transferred, TRANSFER_TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            throw new LibUsbException("Unable to send data", result);
        }
        log.info(transferred.get() + " bytes sent to device");

        //			return deviceConnection.bulkTransfer(outEndpoint, buffer, length, TRANSFER_TIMEOUT);

        return LibUsb.SUCCESS;
    }

    @Override
    public int bulkOutTransfer(byte[] data, int offset, int length)  throws LibAumsException{
        // TODO implement Usb4JavaUsbDeviceCommunication

        if (offset==0) {
            return bulkOutTransfer(data, length);
        }

        log.info("BulkOutTransfer (data={},length={},offset={})", data, length, offset);

        int remaining = length-offset;
        byte[] newData = new byte[length-offset];
        System.arraycopy(data, offset, newData, 0, remaining);

        ByteBuffer buffer = BufferUtils.allocateByteBuffer(newData.length);
        buffer.put(newData);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(deviceHandle,  deviceConfig.getOutEndpointAddress(), buffer,
                transferred, TRANSFER_TIMEOUT);
        if (result != LibUsb.SUCCESS)
        {
            throw LibAumsException.createWithLibUsbException("Unable to send data", result);
        }
        log.info(transferred.get() + " bytes sent to device");

        return LibUsb.SUCCESS;

//			if (offset == 0)
//				return deviceConnection.bulkTransfer(outEndpoint, buffer, length, TRANSFER_TIMEOUT);
//
//			byte[] tmpBuffer = new byte[length];
//			System.arraycopy(buffer, offset, tmpBuffer, 0, length);
//			return deviceConnection.bulkTransfer(outEndpoint, tmpBuffer, length,
//					TRANSFER_TIMEOUT);


    }

    @Override
    public int bulkInTransfer(byte[] data, int length) throws LibAumsException {

        log.info("BulkInTransfer (data={},length={})", data, length);

        // TODO bulkInTransfer, Libusb does things different than android

        ByteBuffer buffer = BufferUtils.allocateByteBuffer(length).order(
                ByteOrder.LITTLE_ENDIAN);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(deviceHandle, deviceConfig.getInEndpointAddress(), buffer,
                transferred, TRANSFER_TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            throw LibAumsException.createWithLibUsbException("Unable to read data", result);
        }
        log.info(transferred.get() + " bytes read from device");
        System.arraycopy(buffer.array(), 0, data, 0, length);

        //        return LibUsb.bulkTransfer(
//                deviceHandle, deviceConfig.getInEndpointAddress(), buffer, length, TRANSFER_TIMEOUT);


        return LibUsb.SUCCESS;
    }

    @Override
    public int bulkInTransfer(byte[] data, int offset, int length)  throws LibAumsException{
        // TODO implement Usb4JavaUsbDeviceCommunication not Sure if this will work ok

        if (offset==0) {
            return bulkInTransfer(data, length);
        }

        log.info("BulkInTransfer (data={},length={},offset={})", data, length, offset);

        ByteBuffer buffer = BufferUtils.allocateByteBuffer(length).order(
                ByteOrder.LITTLE_ENDIAN);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(deviceHandle, deviceConfig.getInEndpointAddress(), buffer,
                transferred, TRANSFER_TIMEOUT);
        if (result != LibUsb.SUCCESS) {
            throw LibAumsException.createWithLibUsbException("Unable to read data", result);
        }
        log.info(transferred.get() + " bytes read from device");
        System.arraycopy(buffer.array(), 0, data, offset, length);

        return LibUsb.SUCCESS;

        //			if (offset == 0)
//				return deviceConnection.bulkTransfer(inEndpoint, buffer, length, TRANSFER_TIMEOUT);
//
//			byte[] tmpBuffer = new byte[length];
//			int result = deviceConnection.bulkTransfer(inEndpoint, tmpBuffer, length,
//					TRANSFER_TIMEOUT);
//			System.arraycopy(tmpBuffer, 0, buffer, offset, length);
//			return result;
    }


    /**
     * Writes some data to the device.
     *
     * @param handle
     *            The device handle.
     * @param data
     *            The data to send to the device.
     */
    public static void write(DeviceHandle handle, byte[] data)  throws LibAumsException
    {
//        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
//        buffer.put(data);
//        IntBuffer transferred = BufferUtils.allocateIntBuffer();
//        int result = LibUsb.bulkTransfer(handle, OUT_ENDPOINT, buffer,
//                transferred, TIMEOUT);
//        if (result != LibUsb.SUCCESS)
//        {
//            throw new LibUsbException("Unable to send data", result);
//        }
//        System.out.println(transferred.get() + " bytes sent to device");
    }

    /**
     * Reads some data from the device.
     *
     * @param handle
     *            The device handle.
     * @param size
     *            The number of bytes to read from the device.
     * @return The read data.
     */
    public static ByteBuffer read(DeviceHandle handle, int size)  throws LibAumsException
    {
//        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(
//                ByteOrder.LITTLE_ENDIAN);
//        IntBuffer transferred = BufferUtils.allocateIntBuffer();
//        int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer,
//                transferred, TIMEOUT);
//        if (result != LibUsb.SUCCESS)
//        {
//            throw new LibUsbException("Unable to read data", result);
//        }
//        System.out.println(transferred.get() + " bytes read from device");
//        return buffer;
        return null;

    }



    public void closeDevice() throws LibAumsException {
        // Release interface
        int result = LibUsb.releaseInterface(deviceHandle, deviceConfig.getInterfaceNumber());
        if (result != LibUsb.SUCCESS) {
            throw LibAumsException.createWithLibUsbException("Unable to release interface", result);
        }

        // Close the device
        LibUsb.close(deviceHandle);
    }
}
