package io.github.carlinhoshk.msavaliadorcredito.application.ex;

import lombok.Getter;

public class ErroComunicacaomicroservicesException extends Exception{
    @Getter
    private Integer status;
    public ErroComunicacaomicroservicesException(String msg, Integer status) {
        super(msg);
        this.status = status;
    }
}
