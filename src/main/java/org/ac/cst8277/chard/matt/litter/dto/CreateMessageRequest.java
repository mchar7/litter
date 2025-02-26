package org.ac.cst8277.chard.matt.litter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.ac.cst8277.chard.matt.litter.model.Message;

/**
 * DTO for create message requests.
 */
@Getter
@Setter
@SuppressWarnings("ClassWithoutLogger")
public class CreateMessageRequest {
    /**
     * The content of the message.
     */
    @JsonProperty
    @Schema(example = Message.EXAMPLE_TEXT, pattern = Message.TEXT_REGEX)
    private String content;
}
