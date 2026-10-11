package com.cgf.order.contracts.models

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumReader
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `OrderStatus` is shared by many events. A consumer that meets a symbol it does not know can only
 * survive it if the enum carries a default; these tests keep that property from eroding.
 */
class OrderStatusContractTest {

    private val recordsCarryingStatus: List<Schema> = listOf(
        OrderEvent.getClassSchema(),
        OrderCreatedEvent.getClassSchema(),
        OrderPaidEvent.getClassSchema(),
        OrderAcceptedEvent.getClassSchema(),
        OrderRejectedEvent.getClassSchema(),
        OrderPreparingEvent.getClassSchema(),
        OrderReadyForPickupEvent.getClassSchema(),
        OrderPickedUpEvent.getClassSchema(),
        OrderDeliveredEvent.getClassSchema(),
        OrderCancelledEvent.getClassSchema(),
        PaymentConfirmedEvent.getClassSchema(),
        PaymentFailedEvent.getClassSchema(),
    )

    @Test
    fun `OrderStatus is defined once, in its own schema file`() {
        val definitions = File("src/main/avro")
            .listFiles { file -> file.extension == "avsc" }!!
            .filter { it.readText().let { text -> text.contains("\"enum\"") && text.contains("\"OrderStatus\"") } }
            .map { it.name }

        assertEquals(listOf("OrderStatus.avsc"), definitions)
    }

    @Test
    fun `every event carrying a status uses the shared enum, which falls back to UNKNOWN`() {
        val shared = OrderStatus.getClassSchema()

        recordsCarryingStatus.forEach { record ->
            val status = statusEnumOf(record)
            assertEquals(shared.fullName, status.fullName, record.name)
            assertEquals(shared.enumSymbols, status.enumSymbols, record.name)
            assertEquals("UNKNOWN", status.enumDefault, record.name)
        }
    }

    @Test
    fun `UNKNOWN is the last symbol and every earlier one is still there`() {
        val symbols = OrderStatus.getClassSchema().enumSymbols

        assertEquals("UNKNOWN", symbols.last())
        assertTrue(
            symbols.containsAll(
                listOf(
                    "CREATED", "PENDING_PAYMENT", "PAYMENT_CONFIRMED", "PAYMENT_FAILED", "ACCEPTED", "REJECTED",
                    "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERED", "COMPLETED", "CANCELED",
                )
            )
        )
    }

    @Test
    fun `a record written with a symbol this version does not know reads back as UNKNOWN`() {
        val reader = OrderAcceptedEvent.getClassSchema()
        val writer = withFutureStatusSymbol(reader, "BRAND_NEW")
        val record = GenericData.Record(writer).apply {
            put("orderId", "order-1")
            put("restaurantId", "11111111-1111-1111-1111-111111111111")
            put("customerId", "22222222-2222-2222-2222-222222222222")
            put("status", GenericData.EnumSymbol(statusEnumOf(writer), "BRAND_NEW"))
        }

        val read = SpecificDatumReader<OrderAcceptedEvent>(writer, reader)
            .read(null, DecoderFactory.get().binaryDecoder(encode(writer, record), null))

        assertEquals(OrderStatus.UNKNOWN, read.status)
        assertEquals("order-1", read.orderId)
    }

    private fun statusEnumOf(record: Schema): Schema {
        val field = record.getField("status").schema()
        return if (field.type == Schema.Type.UNION) field.types.first { it.type == Schema.Type.ENUM } else field
    }

    /** The same record as [record], from a future contract whose `status` enum has one more symbol. */
    private fun withFutureStatusSymbol(record: Schema, symbol: String): Schema {
        val fieldSchema = record.getField("status").schema()
        val current = statusEnumOf(record)
        val grown = Schema.createEnum(current.name, current.doc, current.namespace, current.enumSymbols + symbol, current.enumDefault)
        val grownField = if (fieldSchema.type == Schema.Type.UNION) {
            Schema.createUnion(fieldSchema.types.map { if (it.type == Schema.Type.ENUM) grown else it })
        } else {
            grown
        }
        val fields = record.fields.map { f ->
            Schema.Field(f.name(), if (f.name() == "status") grownField else f.schema(), f.doc(), f.defaultVal())
        }
        return Schema.createRecord(record.name, record.doc, record.namespace, false, fields)
    }

    private fun encode(schema: Schema, record: GenericRecord): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(out, null)
        GenericDatumWriter<GenericRecord>(schema).write(record, encoder)
        encoder.flush()
        return out.toByteArray()
    }
}
