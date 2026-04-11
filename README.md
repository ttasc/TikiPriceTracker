# Download

1. Go to latest release [here](https://github.com/ttasc/TikiPriceTracker/releases/latest)

2. Download the file `TikiTracker-Server.jar` for server

3. Download the file `TikiTracker-Client.jar` for client

# Run

1. Run `TikiTracker-Server.jar` for server:

    ```
    java -jar TikiTracker-Server.jar
    ```

2. Run `TikiTracker-Client.jar` for client, note that you can specify the server ip. Otherwise, the client will receive the IP address from the server's *broadcast* packet.:

    ```
    java -jar TikiTracker-Client.jar [server ip]
    ```

## How to use

### Server

Server will automatically update the price of products have been crawled every 3 hours in the background.

### Crawling For Testing

To crawl over 4000 tiki products, download `TikiTracker-Crawler.jar` from [releases page](https://github.com/ttasc/TikiPriceTracker/releases/latest) and run it everyday in one month.
