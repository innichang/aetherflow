package io.github.inni.aetherflow.support;

import org.testcontainers.DockerClientFactory;

public final class DockerAvailability {

	private DockerAvailability() {
	}

	public static boolean isDockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		} catch (Throwable ignored) {
			return false;
		}
	}
}

