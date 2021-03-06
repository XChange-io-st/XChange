package org.knowm.xchange.cryptopia.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.knowm.xchange.cryptopia.Cryptopia;
import org.knowm.xchange.cryptopia.CryptopiaAdapters;
import org.knowm.xchange.cryptopia.CryptopiaExchange;
import org.knowm.xchange.cryptopia.dto.CryptopiaBaseResponse;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.exceptions.ExchangeException;

public class CryptopiaTradeServiceRaw extends CryptopiaBaseService {

  private final CryptopiaExchange exchange;

  public CryptopiaTradeServiceRaw(CryptopiaExchange exchange) {

    super(exchange);

    this.exchange = exchange;
  }

  public List<LimitOrder> getOpenOrders(CurrencyPair currencyPair, Integer count) throws IOException {

    CryptopiaBaseResponse<List<Map>> response = cryptopia.getOpenOrders(signatureCreator, new Cryptopia.GetOpenOrdersRequest(currencyPair == null ? null : currencyPair.toString(), count));
    if (!response.isSuccess())
      throw new ExchangeException("Failed to get open orders: " + response.toString());

    List<LimitOrder> results = new ArrayList<>();
    for (Map map : response.getData()) {

      Order.OrderType type = type(map);

      BigDecimal originalAmount = new BigDecimal(map.get("Amount").toString());
      BigDecimal remaining = new BigDecimal(map.get("Remaining").toString());
      BigDecimal total = new BigDecimal(map.get("Total").toString());

      String id = map.get("OrderId").toString();
      Date timestamp = CryptopiaAdapters.convertTimestamp(map.get("TimeStamp").toString());

      // asd
      BigDecimal limitPrice = new BigDecimal(map.get("Rate").toString());
      BigDecimal averagePrice = null;
      BigDecimal cumulativeAmount = originalAmount.subtract(remaining);
      Order.OrderStatus status = Order.OrderStatus.PENDING_NEW;

      CurrencyPair pair = new CurrencyPair(map.get("Market").toString());
      results.add(new LimitOrder(
          type,
          originalAmount,
          pair,
          id,
          timestamp,
          limitPrice,
          averagePrice,
          cumulativeAmount,
          status
      ));
    }

    return results;
  }

  public String submitTrade(CurrencyPair currencyPair, LimitOrder.OrderType type, BigDecimal price, BigDecimal amount) throws IOException {
    String rawType = type.equals(Order.OrderType.BID) ? "Buy" : "Sell";

    CryptopiaBaseResponse<Map> response = cryptopia.submitTrade(signatureCreator, new Cryptopia.SubmitTradeRequest(currencyPair.toString(), rawType, price, amount));
    if (!response.isSuccess())
      throw new ExchangeException("Failed to submit order: " + response.toString());

    List<Integer> filled = (List<Integer>) response.getData().get("FilledOrders");
    if (filled.isEmpty()) {
      return response.getData().get("OrderId").toString();
    } else {
      //if it fills instantly we don't get an orderId, so instead we return the 1st fillId...  far from perfect because there could be many
      return filled.get(0).toString();
    }
  }

  public boolean cancel(String orderId) throws IOException {
    CryptopiaBaseResponse<List> response = cryptopia.cancelTrade(signatureCreator, new Cryptopia.CancelTradeRequest("Trade", orderId, null));
    if (!response.isSuccess())
      throw new ExchangeException("Failed to cancel order " + orderId + ": " + response.toString());

    return !response.getData().isEmpty();
  }

  public boolean cancelAll(CurrencyPair currencyPair) throws IOException {
    Long marketId = currencyPair == null ? null : exchange.tradePairId(currencyPair);
    CryptopiaBaseResponse<List> response = cryptopia.cancelTrade(signatureCreator, new Cryptopia.CancelTradeRequest("TradePair", null, marketId));
    if (!response.isSuccess())
      throw new ExchangeException("Failed to cancel orders for pair " + currencyPair + ": " + response.toString());
    return !response.getData().isEmpty();
  }

  public List<UserTrade> tradeHistory(CurrencyPair currencyPair, Integer count) throws IOException {
    CryptopiaBaseResponse<List<Map>> response = cryptopia.getTradeHistory(signatureCreator, new Cryptopia.GetTradeHistoryRequest(currencyPair == null ? null : currencyPair.toString(), count == null ? 100 : count));
    if (!response.isSuccess())
      throw new ExchangeException("Failed to get trade history: " + response.toString());

    List<UserTrade> results = new ArrayList<>();
    for (Map map : response.getData()) {
      Order.OrderType type = type(map);
      BigDecimal amount = new BigDecimal(map.get("Amount").toString());
      BigDecimal price = new BigDecimal(map.get("Rate").toString());
      Date timestamp = CryptopiaAdapters.convertTimestamp(map.get("TimeStamp").toString());
      String id = map.get("TradeId").toString();
      BigDecimal fee = new BigDecimal(map.get("Fee").toString());
      String orderId = id;

      CurrencyPair pair = new CurrencyPair(map.get("Market").toString());
      Currency feeCcy = pair.counter;
      results.add(new UserTrade(
          type,
          amount,
          pair,
          price,
          timestamp,
          id,
          orderId,
          fee,
          feeCcy
      ));
    }

    return results;
  }

  private static Order.OrderType type(Map map) {
    return map.get("Type").toString().equals("Buy") ? Order.OrderType.BID : Order.OrderType.ASK;
  }
}
