# Project Overview

This repository contains projects for data acquisition from smartwatches and an API server to receive and save the data. The projects are organized into the following subdirectories:

## Subdirectories

### Android
This directory contains the project for data acquisition from Android devices. The project is built using Kotlin and leverages the Android SDK to access the gyroscope and accelerometer sensors. The data collected is sent to the API server for further processing.

### Flask-save-data-on-file
This directory contains the Flask Web application for handling data acquisition from smartwatches. The server is set up to listen for incoming POST requests containing motion data (gyroscope and accelerometer) and saves the data to files for later analysis.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
