package io.syndesis.qe.validation;

import io.syndesis.qe.utils.OpenShiftUtils;
import io.syndesis.qe.utils.TestUtils;
import io.syndesis.qe.utils.mqtt.MqttUtils;
import io.syndesis.qe.utils.mqtt.Receiver;

import org.assertj.core.api.Assertions;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.IOException;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.LocalPortForward;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MqttValidationSteps {
    private MqttUtils mqttUtils = new MqttUtils();
    private static LocalPortForward mqttLocalPortForward = null;

    @Then("verify that when message is sent to {string} topic it is redirected to {string} topic via integration")
    public void sendAndRecieveMessage(String senderTopic, String receiverTopic) {

        //reset message received flag
        Receiver.RECEIVED_FLAG = 0;

        MqttClient receiverClient = null;
        try {
            portForward();

            //create receiver
            receiverClient = mqttUtils.createReceiver("receiver_1", receiverTopic);

            //send message via client
            mqttUtils.sendMessage("Hi from syndesis integration!", senderTopic);

            //give it some short time for delivery
            TestUtils.sleepIgnoreInterrupt(4000);
        } catch (MqttException e) {
            Assertions.fail("Mqtt Exception was thrown during message transfer", e);
        } finally {
            //close receiver
            mqttUtils.closeClient(receiverClient);

            //close mqtt port
            portClose();
        }

        //check flag
        Assertions.assertThat(Receiver.RECEIVED_FLAG).as("Message was not received!")
                .isEqualTo(1);
    }

    @When("^send mqtt message to \"([^\"]*)\" topic$")
    public void sendMqttMessage(String senderTopic) {
        try {
            portForward();

            //send message via client
            mqttUtils.sendMessage("{\"key\" : 1,\"value\" : \"FirstValue\"}", senderTopic);

            //give it some short time for delivery
            TestUtils.sleepIgnoreInterrupt(4000);
        } finally {
            //close mqtt port
            portClose();
        }
    }

    private void portForward() {
        if (mqttLocalPortForward == null || !mqttLocalPortForward.isAlive()) {
            Pod pod = OpenShiftUtils.getInstance().getAnyPod("app", "syndesis-amq");
            log.info("POD NAME: *{}*", pod.getMetadata().getName());
            mqttLocalPortForward = OpenShiftUtils.portForward(pod, 1883, 1883);
            //give it time to get ready
            TestUtils.sleepIgnoreInterrupt(2000);
        }
    }

    private void portClose() {
        if (mqttLocalPortForward != null && mqttLocalPortForward.isAlive()) {
            try {
                mqttLocalPortForward.close();
            } catch (IOException e) {
                log.error("Error while closing mqtt port forward.", e);
            }
        }
    }
}
