package me.daehokimm;

import org.apache.kafka.clients.producer.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class ImageProducer {

	private static final String TOPIC_NAME = "chucked-image";
	private static final String IMAGE_NAME = "over_max_size.jpg";        // or `small_size.jpg`
	private static final String IMAGE_DIR = "images/";
	private static final int IMAGE_SEGMENT_SIZE = 500_000;

	public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

		// broker configure
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.UUIDSerializer");
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "me.daehokimm.ImageChunkSerializer");        // custom serializer`

		// read file & convert to byte[]
		Path path = Paths.get(IMAGE_DIR + IMAGE_NAME);
		byte[] bytes = Files.readAllBytes(path);
		System.out.println("== total image size : " + bytes.length);

		if (bytes.length == 0)
			return;

		// chop the large size image file
		// to small images that under `request.max.size`
		long ts = System.currentTimeMillis();
		int totalParts = (bytes.length / IMAGE_SEGMENT_SIZE) + 1;
		int partNum = 0;
		List<ImageChunk> imageChunks = new ArrayList<>();
		while (partNum * IMAGE_SEGMENT_SIZE < bytes.length) {
			int byteSize = IMAGE_SEGMENT_SIZE;
			if ((partNum + 1) * IMAGE_SEGMENT_SIZE > bytes.length)        // for last parts
				byteSize = bytes.length - (partNum * IMAGE_SEGMENT_SIZE);

			byte[] chuckBytes = new byte[byteSize];
			System.arraycopy(bytes, partNum * IMAGE_SEGMENT_SIZE, chuckBytes, 0, byteSize);
			imageChunks.add(new ImageChunk(IMAGE_NAME, ts, totalParts, partNum, chuckBytes));

			partNum++;
		}

		// initialize producer & send records
		Producer<UUID, ImageChunk> producer = new KafkaProducer<>(props);
		UUID uuid = UUID.randomUUID();
		for (ImageChunk imageChunk : imageChunks) {
			ProducerRecord<UUID, ImageChunk> record = new ProducerRecord<>(TOPIC_NAME, uuid, imageChunk);
			RecordMetadata recordMetadata = producer.send(record).get();
			printResult(recordMetadata);
		}
	}

	private static void printResult(RecordMetadata recordMetadata) {
		System.out.println("== send result");
		System.out.println("* partition  : " + recordMetadata.partition());
		System.out.println("* offset     : " + recordMetadata.offset());
		System.out.println("* timestamp  : " + recordMetadata.timestamp());
		System.out.println("* value size : " + recordMetadata.serializedValueSize());
	}
}
