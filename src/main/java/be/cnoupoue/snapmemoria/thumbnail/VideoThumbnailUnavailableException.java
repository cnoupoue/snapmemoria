package be.cnoupoue.snapmemoria.thumbnail;

import be.cnoupoue.snapmemoria.web.ApiException;
import org.springframework.http.HttpStatus;

public class VideoThumbnailUnavailableException extends ApiException {

  public VideoThumbnailUnavailableException() {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "VIDEO_THUMBNAIL_UNAVAILABLE",
        "Video preview generation is unavailable, but the original video can still be opened.");
  }
}
