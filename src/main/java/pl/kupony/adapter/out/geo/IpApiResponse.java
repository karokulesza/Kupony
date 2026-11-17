package pl.kupony.adapter.out.geo;

record IpApiResponse(String status, String message, String countryCode, Boolean proxy, Boolean hosting) {

    boolean isSuccess() {
        return "success".equals(status);
    }
}
