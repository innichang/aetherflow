package io.github.inni.aetherflow.examples.order_fulfillment.workflow;

import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
@AIWorkflow("order-fulfillment-workflow")
public class OrderFulfillmentWorkflow {

	@Step
	public void validateOrder() {
		System.out.println("Validating order");
		sleepMillis(40);
	}

	@Step(dependsOn = {"validateOrder"})
	public void reserveInventory() {
		System.out.println("Reserving inventory");
		sleepMillis(40);
	}

	@Step(dependsOn = {"reserveInventory"})
	public void chargePayment() {
		System.out.println("Charging payment");
		sleepMillis(40);
	}

	@Step(dependsOn = {"chargePayment"})
	public void createShipment() {
		System.out.println("Creating shipment");
		sleepMillis(40);
	}

	@Step(dependsOn = {"createShipment"})
	public void sendConfirmation() {
		System.out.println("Sending confirmation");
		sleepMillis(40);
	}

	private void sleepMillis(long millis) {
		try {
			TimeUnit.MILLISECONDS.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}
}

