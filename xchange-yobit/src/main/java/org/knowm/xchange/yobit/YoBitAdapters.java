package org.knowm.xchange.yobit;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.marketdata.Trades.TradeSortType;
import org.knowm.xchange.dto.meta.CurrencyMetaData;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrade;
import org.knowm.xchange.utils.DateUtils;
import org.knowm.xchange.yobit.dto.marketdata.YoBitAsksBidsData;
import org.knowm.xchange.yobit.dto.marketdata.YoBitInfo;
import org.knowm.xchange.yobit.dto.marketdata.YoBitOrderBook;
import org.knowm.xchange.yobit.dto.marketdata.YoBitPair;
import org.knowm.xchange.yobit.dto.marketdata.YoBitPairs;
import org.knowm.xchange.yobit.dto.marketdata.YoBitTicker;
import org.knowm.xchange.yobit.dto.marketdata.YoBitTrade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.apache.commons.lang3.StringUtils.join;

public class YoBitAdapters {

  public static CurrencyPair adaptCurrencyPair(String pair) {
    String[] currencies = pair.toUpperCase().split("_");
    if(currencies.length != 2)
      throw new IllegalStateException("Cannot parse currency pair: " + pair);
    return new CurrencyPair(adaptCurrency(currencies[0]), adaptCurrency(currencies[1]));
  }

  public static Currency adaptCurrency(String ccy) {
    return Currency.getInstance(ccy.toUpperCase());
  }

  public static OrderBook adaptOrderBook(YoBitOrderBook book, CurrencyPair currencyPair) {

    List<LimitOrder> asks = toLimitOrderList(book.getAsks(), OrderType.ASK, currencyPair);
    List<LimitOrder> bids = toLimitOrderList(book.getBids(), OrderType.BID, currencyPair);

    return new OrderBook(null, asks, bids);
  }

  public static ExchangeMetaData adaptToExchangeMetaData(ExchangeMetaData exchangeMetaData, YoBitInfo products) {
    Map<CurrencyPair, CurrencyPairMetaData> currencyPairs = exchangeMetaData.getCurrencyPairs();
    Map<Currency, CurrencyMetaData> currencies = exchangeMetaData.getCurrencies();

    YoBitPairs pairs = products.getPairs();
    Map<CurrencyPair, YoBitPair> price = pairs.getPrice();

    for (Entry<CurrencyPair, YoBitPair> entry : price.entrySet()) {
      CurrencyPair pair = entry.getKey();
      YoBitPair value = entry.getValue();

      BigDecimal minSize = value.getMin_amount();
      Integer priceScale = value.getDecimal_places();
      currencyPairs.put(pair, new CurrencyPairMetaData(value.getFee(), minSize, null, priceScale));

      if (!currencies.containsKey(pair.base))
        currencies.put(pair.base, new CurrencyMetaData(8));

      if (!currencies.containsKey(pair.counter))
        currencies.put(pair.counter, new CurrencyMetaData(8));
    }

    return exchangeMetaData;
  }

  private static List<LimitOrder> toLimitOrderList(List<YoBitAsksBidsData> levels, OrderType orderType, CurrencyPair currencyPair) {

    List<LimitOrder> allLevels = new ArrayList<>(levels.size());
    for (int i = 0; i < levels.size(); i++) {
      YoBitAsksBidsData ask = levels.get(i);
      if (ask != null) {
        allLevels.add(new LimitOrder(orderType, ask.getQuantity(), currencyPair, "0", null, ask.getRate()));
      }
    }

    return allLevels;

  }

  public static Trades adaptTrades(List<YoBitTrade> ctrades, CurrencyPair currencyPair) {
    List<Trade> trades = new ArrayList<>(ctrades.size());

    int lastTrade = 0;

    for (int i = 0; i < ctrades.size(); i++) {
      YoBitTrade trade = ctrades.get(i);

      OrderType type = trade.getType().equals("bid") ? OrderType.BID : OrderType.ASK;

      Trade t = new Trade(type, trade.getAmount(), currencyPair, trade.getPrice(), parseDate(trade.getTimestamp()), String.valueOf(trade.getTid()));
      trades.add(t);
      lastTrade = i;
    }

    return new Trades(trades, ctrades.get(lastTrade).getTid(), TradeSortType.SortByID);
  }

  private static Date parseDate(Long rawDateLong) {
    return new Date(rawDateLong * 1000);
  }

  public static Ticker adaptTicker(YoBitTicker ticker, CurrencyPair currencyPair) {
    Ticker.Builder builder = new Ticker.Builder();

    builder.currencyPair(currencyPair);
    builder.last(ticker.getLast());
    builder.bid(ticker.getBuy());
    builder.ask(ticker.getSell());
    builder.high(ticker.getHigh());
    builder.low(ticker.getLow());
    builder.volume(ticker.getVolCur());
    builder.timestamp(new Date(ticker.getUpdated() * 1000L));

    return builder.build();
  }

  public static String adaptCcyPairsToUrlFormat(Iterable<CurrencyPair> currencyPairs) {
    List<String> pairs = new ArrayList<>();

    for (CurrencyPair currencyPair : currencyPairs) {
      pairs.add(adaptCcyPairToUrlFormat(currencyPair));
    }

    return join(pairs, "-");
  }

  public static String adaptCcyPairToUrlFormat(CurrencyPair currencyPair) {
    return currencyPair.base.getCurrencyCode().toLowerCase() + "_" + currencyPair.counter.getCurrencyCode().toLowerCase();
  }

  public static OrderType adaptType(String type) {
    return type.equalsIgnoreCase("sell") ? OrderType.ASK : OrderType.BID;
  }

  public static Order.OrderStatus adaptOrderStatus(String status) {
    Order.OrderStatus orderStatus = Order.OrderStatus.PARTIALLY_FILLED;
    switch (status) {
      case "0": {
        orderStatus = Order.OrderStatus.NEW;
        break;
      }
      case "1": {
        orderStatus = Order.OrderStatus.FILLED;
        break;
      }
      case "2": {
        orderStatus = Order.OrderStatus.CANCELED;
        break;
      }
      case "3": {
        orderStatus = Order.OrderStatus.STOPPED;
      }
    }
    return orderStatus;
  }

  public static LimitOrder adaptOrder(String orderId, Map map) {
    String pair = map.get("pair").toString();
    String type = map.get("type").toString();
//        String initialAmount = map.get("start_amount").toString();
    String amountRemaining = map.get("amount").toString();
    String rate = map.get("rate").toString();
    String timestamp = map.get("timestamp_created").toString();
    String status = map.get("status").toString();//status: 0 - active, 1 - fulfilled and closed, 2 - cancelled, 3 - cancelled after partially fulfilled.

    Date time = DateUtils.fromUnixTime(Long.valueOf(timestamp));

    Order.OrderStatus orderStatus = adaptOrderStatus(status);

    return new LimitOrder(
        adaptType(type),
        new BigDecimal(amountRemaining),
        adaptCurrencyPair(pair),
        orderId,
        time,
        new BigDecimal(rate),
        null,
        null,
        orderStatus
    );
  }

  public static UserTrade adaptUserTrade(Object key, Map tradeData) {
    String id = key.toString();
    String type = tradeData.get("type").toString();
    String amount = tradeData.get("amount").toString();
    String rate = tradeData.get("rate").toString();
    String orderId = tradeData.get("order_id").toString();
    String pair = tradeData.get("pair").toString();
    String timestamp = tradeData.get("timestamp").toString();

    Date time = DateUtils.fromUnixTime(Long.valueOf(timestamp));

    return new UserTrade(
        adaptType(type),
        new BigDecimal(amount),
        adaptCurrencyPair(pair),
        new BigDecimal(rate),
        time,
        id,
        orderId,
        null,
        null
    );
  }
}
