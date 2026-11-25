package com.example.senioron.domain.family.repository.projection;

public interface FamilyPhotoAlbumCountProjection {

    Long getUploaderUserId();

    Long getPhotoCount();

    Long getNewPhotoCount();
}
