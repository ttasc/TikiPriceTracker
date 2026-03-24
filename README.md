# Download

1. Go to latest release [here](https://github.com/ttasc/TikiPriceTracker/releases/latest)

2. Download the file `TikiTracker-Server.jar` for server

3. Download the file `TikiTracker-Client.jar` for client

# Run

1. Run `TikiTracker-Server.jar` for server:

    ```
    java -jar TikiTracker-Server.jar
    ```

2. Run `TikiTracker-Client.jar` for client, note that you can specify the server ip, default is `localhost`:

    ```
    java -jar TikiTracker-Client.jar [server ip]
    ```

## How to use

### Server

> #### Note:
> - You need to run the server first before the client
> - The current version cannot automatically update prices; you have to run a manual update command through the server prompt.

##### Update the price of all tracked products:

In the server console, type `update` to run the update.
