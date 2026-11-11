package evm.stat.client.exceptions;

public class StatsServerUnavailable extends RuntimeException {
    public StatsServerUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}