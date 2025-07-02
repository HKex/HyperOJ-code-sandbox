package com.hkex.hyperojcodesandbox.model;

import lombok.Data;

/**
 * 运行信息
 */
@Data
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;
}
