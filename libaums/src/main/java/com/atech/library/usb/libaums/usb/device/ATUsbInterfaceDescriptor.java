/*
 * (C) Copyright 2024 Andy Rozman <andy.rozman@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.atech.library.usb.libaums.usb.device;

import lombok.Data;
import lombok.experimental.Accessors;
import org.usb4java.EndpointDescriptor;
import org.usb4java.InterfaceDescriptor;

import javax.usb.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by andy on 10.04.2024.
 */
@Data
@Accessors(fluent = true)
public class ATUsbInterfaceDescriptor implements UsbInterfaceDescriptor {

    byte bLength;

    byte bDescriptorType;

    byte bInterfaceNumber;

    byte bAlternateSetting;

    byte bNumEndpoints;

    byte bInterfaceClass;

    byte bInterfaceSubClass;

    byte bInterfaceProtocol;

    byte iInterface;

    //EndpointDescriptor[] endpoint();

    List<ATUsbEndpointDescriptor> endpoints;

    ByteBuffer extra;

    int extraLength;

    ATUsbConfigurationDescriptor atUsbConfigurationDescriptor;

    public ATUsbInterfaceDescriptor(ATUsbConfigurationDescriptor atUsbConfigurationDescriptor) {
        this.atUsbConfigurationDescriptor = atUsbConfigurationDescriptor;
    }


    public void loadData(InterfaceDescriptor interfaceDescriptor) {
        bLength = interfaceDescriptor.bLength();
        bDescriptorType = interfaceDescriptor.bDescriptorType();
        bInterfaceNumber=interfaceDescriptor.bInterfaceNumber();
        bAlternateSetting = interfaceDescriptor.bAlternateSetting();
        bNumEndpoints = interfaceDescriptor.bNumEndpoints();
        bInterfaceClass = interfaceDescriptor.bInterfaceClass();
        bInterfaceSubClass = interfaceDescriptor.bInterfaceSubClass();
        bInterfaceProtocol = interfaceDescriptor.bInterfaceProtocol();
        iInterface = interfaceDescriptor.iInterface();

        EndpointDescriptor[] endpoint = interfaceDescriptor.endpoint();
        this.endpoints = new ArrayList<>();

        for (EndpointDescriptor endpointDescriptor : endpoint) {
            ATUsbEndpointDescriptor endpDescriptor = new ATUsbEndpointDescriptor();
            endpDescriptor.loadData(endpointDescriptor);
            this.endpoints.add(endpDescriptor);
        }
    }


    public ATUsbEndpointDescriptor getEndpoint(int j) {
        return this.endpoints.get(j);
    }

    public String toLsUsbString(int pad) {

//        byte bLength;
//
//        byte bDescriptorType;
//
//        byte bInterfaceNumber;
//
//        byte bAlternateSetting;
//
//        byte bNumEndpoints;
//
//        byte bInterfaceClass;
//
//        byte bInterfaceSubClass;
//
//        byte bInterfaceProtocol;
//
//        byte iInterface;


        return "XXATUsbDevice: " + toString();
    }


}
