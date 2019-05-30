# AC Bluetooth Switch
ECE 342 Final Project: AC Bluetooth Switch
Author: Suyang Liu
Oregon State University
Spring 2019

An Android App written in Java using Android Studio. Supports Android API 21 and higher.
Designed to remotely control a 2 outlet device that plugs into a standard US AC wall outlet. Device supports loads drawing up to 5A. Outlet shutoff timers can be set for each outlet individually and are fully customizable.

App connects to a microcontroller over Bluetooth LE using UART. Receives information on device reporting status of outlet, current draw, current timer settings. A software overcurrent shutoff warning is monitored for.
App sends data indicating outlet state switches and timer settings.
