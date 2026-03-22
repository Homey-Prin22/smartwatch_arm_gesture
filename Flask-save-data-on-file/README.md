### 1. Install Development Dependencies and Update settings
- Install requirements:
  ```sh
  pip install -r requirements.txt
  ```
- Modify the host IP address at line 144 of the `exp_manager.py` file to the correct value.

### 2. Run the Web application
```sh
python exp_manager.py
```

### 3. Access the Server
Open a browser and go to:
```
http://<id_address>:8080
```

## Run data acquisition
The data acquisition process is overseen by the administrator. It begins with participant registration, during which each participant is assigned a unique ID in the format `<letter><number>`. The letter denotes the participant's affiliation (**B** for Bicocca, **P** for Pavia, and **S** for Salerno), while the number is assigned incrementally, ensuring no duplicate user IDs. After registration, the administrator logs in and instructs the participant to perform the gestures displayed on the browser. The administrator's role is limited to accepting or rejecting the recorded data for each acquisition.