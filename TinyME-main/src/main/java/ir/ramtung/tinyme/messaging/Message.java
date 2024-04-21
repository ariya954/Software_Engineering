package ir.ramtung.tinyme.messaging;

public class Message {
    public static final String INVALID_ORDER_ID = "Invalid order ID";
    public static final String ORDER_QUANTITY_NOT_POSITIVE = "Order quantity is not-positive";
    public static final String ORDER_PRICE_NOT_POSITIVE = "Order price is not-positive";
    public static final String MINIMUM_EXECUTION_QUANTITY_IS_NEGATIVE = "Minimum execution quantity is negative";
    public static final String MINIMUM_EXECUTION_QUANTITY_IS_GREATER_THAN_ORDER_QUANTITY = "Minimum execution quantity is greater than order quantity";
    public static final String STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG_OR_HAVE_MINIMUM_EXECUTION_QUANTITY = "Stop limit order cannot be Iceberg or have minimum execution quantity";
    public static final String UNKNOWN_SECURITY_ISIN = "Unknown security ISIN";
    public static final String ORDER_ID_NOT_FOUND = "Order ID not found in the order book";
    public static final String INVALID_PEAK_SIZE = "Iceberg order peak size is out of range";
    public static final String CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER = "Cannot specify peak size for a non-iceberg order";
    public static final String UNKNOWN_BROKER_ID = "Unknown broker ID";
    public static final String UNKNOWN_SHAREHOLDER_ID = "Unknown shareholder ID";
    public static final String BUYER_HAS_NOT_ENOUGH_CREDIT = "Buyer has not enough credit";
    public static final String QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE = "Quantity is not a multiple of security lot size";
    public static final String PRICE_NOT_MULTIPLE_OF_TICK_SIZE = "Price is not a multiple of security tick size";
    public static final String SELLER_HAS_NOT_ENOUGH_POSITIONS = "Seller has not enough positions";
    public static final String THE_EXECUTED_QUANTITY_OF_REQUESTED_ORDER_IS_LESS_THAN_MINIMUM_EXECUTION_QUANTITY = "The executed quantity of requested order is less than minimum execution quantity";
    public static final String CHANGING_THE_MINIMUM_EXECUTION_QUANTITY_DURING_UPDATING_AN_ORDER_IS_NOT_ALLOWED = "Changing the minimum execution quantity during updating an order is not allowed";
}