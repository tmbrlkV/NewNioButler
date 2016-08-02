package com.butler.client;

class ResponseHandler {
    private byte[] rsp = null;

    synchronized boolean handleResponse(byte[] rsp) {
        this.rsp = rsp;
        this.notify();
        return true;
    }

    synchronized String waitForResponse() {
        while(this.rsp == null) {
            try {
                this.wait();
            } catch (InterruptedException ignored) {
            }
        }

        String reply = new String(this.rsp);
        rsp = null;
        return reply;
    }
}
