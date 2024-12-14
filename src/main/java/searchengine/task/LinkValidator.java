//package searchengine.task;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//
//public class LinkValidator {
//
//    public static boolean isValid(String linkHref, String baseDomain) {
//        try {
//            URI uri = new URI(linkHref);
//            String linkDomain = uri.getHost();
//
//            String normalizedBaseDomain = normalizeDomain(baseDomain);
//            String normalizedLinkDomain = normalizeDomain(linkDomain);
//
//            String scheme = uri.getScheme();
//            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
//                return false;
//            }
//
//            if (linkDomain == null) return true;
//
//            if (!normalizedBaseDomain.equals(normalizedLinkDomain)) {
//                return false;
//            }
//
//            if (uri.getFragment() != null || linkHref.isEmpty()) {
//                return false;
//            }
//
//            String path = uri.getPath();
//            return path == null || (!path.endsWith(".pdf") && !path.endsWith(".jpg") &&
//                    !path.endsWith(".png") && !path.endsWith(".zip"));
//
//        } catch (URISyntaxException e) {
//            return false;
//        }
//    }
//
//    private static String normalizeDomain(String domain) {
//        if (domain == null) return null;
//        String[] parts = domain.split("\\.");
//        if (parts.length > 2) {
//            return parts[parts.length - 2] + "." + parts[parts.length - 1];
//        }
//        return domain;
//    }
//}
//
