package com.mindsetalliance.core.iam;

import com.mindsetalliance.core.common.BusinessException;

import java.util.Base64;
import java.util.Set;

public final class PhotoProfilUtil {

    public static final int MAX_BYTES = 5_000_000;
    private static final Set<String> MIMES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private PhotoProfilUtil() {}

    public static void apply(Agent agent, String photo) {
        int comma = photo.indexOf(',');
        if (comma < 0 || !photo.startsWith("data:")) {
            throw new BusinessException("La photo de profil est invalide");
        }
        String header = photo.substring(5, comma);
        String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
        if (!MIMES.contains(mime)) {
            throw new BusinessException("Le format de photo n’est pas accepté (JPEG, PNG ou WebP)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(photo.substring(comma + 1));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("La photo de profil est invalide");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("La photo de profil ne doit pas dépasser 5 Mo");
        }
        agent.setPhotoMime(mime);
        agent.setPhotoProfil(bytes);
    }

    public static boolean hasPhoto(Agent agent) {
        return agent != null && agent.getPhotoProfil() != null && agent.getPhotoProfil().length > 0;
    }

    public static String toDataUrl(Agent agent) {
        if (!hasPhoto(agent)) {
            return null;
        }
        String mime = agent.getPhotoMime() == null ? "image/jpeg" : agent.getPhotoMime();
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(agent.getPhotoProfil());
    }
}
