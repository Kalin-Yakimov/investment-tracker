export default function PriceAttribution() {
  return <div className="text-end small text-secondary mt-3">
    Prices: <a className="text-secondary" href="https://csgotrader.app/prices/" target="_blank" rel="noopener noreferrer">CSGOTrader</a>
    {" · "}<a className="text-secondary" href="https://www.exchangerate-api.com" target="_blank" rel="noopener noreferrer">Rates by ExchangeRate-API</a>
  </div>;
}
