package com.hkex.hyperojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;

public class DockerDemo {

    public static void main(String[] args) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        System.out.println("hi");
    }
}
