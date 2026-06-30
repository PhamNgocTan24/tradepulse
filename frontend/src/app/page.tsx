"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { 
  TrendingUp, 
  Zap, 
  ShieldCheck, 
  Layers, 
  ArrowRight, 
  ChevronRight, 
  Activity, 
  Server,
  DollarSign
} from "lucide-react";

// ============================================================================
// SIMULATED INITIAL TICKER VALUES
// ============================================================================
interface TickerData {
  symbol: string;
  name: string;
  price: number;
  change: number;
  high: number;
  low: number;
  volume: string;
}

const INITIAL_TICKERS: TickerData[] = [
  { symbol: "BTCUSDT", name: "Bitcoin", price: 64250.50, change: 2.45, high: 64500.00, low: 62100.00, volume: "12,485 BTC" },
  { symbol: "ETHUSDT", name: "Ethereum", price: 3480.20, change: -0.85, high: 3560.00, low: 3410.00, volume: "95,120 ETH" },
  { symbol: "SOLUSDT", name: "Solana", price: 142.75, change: 5.12, high: 145.20, low: 132.50, volume: "524,800 SOL" },
];

export default function LandingPage() {
  const [tickers, setTickers] = useState<TickerData[]>(INITIAL_TICKERS);
  const [activeTab, setActiveTab] = useState<"markets" | "tech">("markets");

  // Mock real-time ticking price effect
  useEffect(() => {
    const interval = setInterval(() => {
      setTickers((prevTickers) =>
        prevTickers.map((ticker) => {
          // Add small random price fluctuation (-0.1% to +0.1%)
          const pct = (Math.random() - 0.48) * 0.001; // slightly biased upwards
          const diff = ticker.price * pct;
          const newPrice = Number((ticker.price + diff).toFixed(2));
          const newChange = Number((ticker.change + pct * 100).toFixed(2));
          return {
            ...ticker,
            price: newPrice,
            change: newChange,
            high: newPrice > ticker.high ? newPrice : ticker.high,
            low: newPrice < ticker.low ? newPrice : ticker.low,
          };
        })
      );
    }, 1500);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 selection:bg-blue-600 selection:text-white">
      
      {/* BACKGROUND EFFECTS */}
      <div className="absolute inset-0 -z-10 overflow-hidden">
        <div className="absolute top-0 left-1/4 h-[500px] w-[500px] rounded-full bg-blue-500/10 blur-3xl" />
        <div className="absolute top-1/3 right-1/4 h-[600px] w-[600px] rounded-full bg-violet-600/10 blur-3xl" />
        <div className="absolute inset-0 bg-[linear-gradient(to_right,#0f172a_1px,transparent_1px),linear-gradient(to_bottom,#0f172a_1px,transparent_1px)] bg-[size:4rem_4rem] [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_70%,transparent_100%)] opacity-30" />
      </div>

      {/* HEADER / NAVIGATION */}
      <header className="sticky top-0 z-50 border-b border-slate-800/60 bg-slate-950/80 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 font-bold text-white shadow-md shadow-blue-500/20">
              TP
            </div>
            <span className="text-xl font-bold tracking-tight text-white">
              Trade<span className="text-blue-500">Pulse</span>
            </span>
          </div>

          <nav className="hidden items-center gap-8 md:flex text-sm font-medium text-slate-400">
            <a href="#markets" className="hover:text-white transition-colors">Markets</a>
            <a href="#features" className="hover:text-white transition-colors">Features</a>
            <a href="#technology" className="hover:text-white transition-colors">Technology</a>
          </nav>

          <div className="flex items-center gap-4">
            <Link 
              href="/login" 
              className="text-sm font-semibold text-slate-300 hover:text-white transition-colors"
            >
              Sign In
            </Link>
            <Link 
              href="/register" 
              className="inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-500 transition-all shadow-md shadow-blue-500/10 cursor-pointer"
            >
              Register
            </Link>
          </div>
        </div>
      </header>

      {/* HERO SECTION */}
      <section className="relative mx-auto max-w-7xl px-6 pt-20 pb-16 text-center lg:pt-32">
        <div className="mx-auto max-w-3xl space-y-6">
          
          {/* BADGE */}
          <div className="inline-flex items-center gap-2 rounded-full border border-blue-500/20 bg-blue-500/5 px-4 py-1.5 text-xs font-medium text-blue-400">
            <Activity className="h-3.5 w-3.5 animate-pulse" />
            Binance Live Stream Outbound Outflows Enabled
          </div>

          {/* MAIN HEADING */}
          <h1 className="text-4xl font-extrabold tracking-tight text-white sm:text-6xl bg-gradient-to-r from-white via-slate-100 to-slate-400 bg-clip-text text-transparent">
            Real-time Crypto Trading. <br className="hidden sm:inline" />
            <span className="bg-gradient-to-r from-blue-400 to-violet-400 bg-clip-text text-transparent">
              Sub-millisecond Execution.
            </span>
          </h1>

          {/* SUBTITLE */}
          <p className="mx-auto max-w-2xl text-base text-slate-400 sm:text-lg">
            Trade with pulse. Experience ultra-low latency order matching powered by a Spring Boot microservice architecture, Apache Kafka pipelines, and live Binance WebSockets.
          </p>

          {/* CTA BUTTONS */}
          <div className="flex flex-col items-center justify-center gap-4 sm:flex-row sm:gap-6 pt-4">
            <Link
              href="/register"
              className="group inline-flex items-center gap-2 rounded-xl bg-blue-600 px-6 py-3.5 font-semibold text-white hover:bg-blue-500 transition-all shadow-lg shadow-blue-500/20 cursor-pointer w-full sm:w-auto justify-center"
            >
              Start Trading Now
              <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </Link>
            <a
              href="#markets"
              className="inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-900/50 px-6 py-3.5 font-semibold text-slate-300 hover:bg-slate-900 hover:text-white hover:border-slate-600 transition-all backdrop-blur-xs w-full sm:w-auto justify-center"
            >
              Explore Markets
            </a>
          </div>
        </div>

        {/* HERO MOCK DASHBOARD WRAPPER */}
        <div className="mt-16 sm:mt-24 rounded-2xl border border-slate-800/80 bg-slate-900/40 p-3 shadow-2xl backdrop-blur-md max-w-5xl mx-auto">
          <div className="rounded-xl border border-slate-800 bg-slate-950/80 p-6 overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
              <div className="flex items-center gap-2">
                <span className="h-3 w-3 rounded-full bg-red-500" />
                <span className="h-3 w-3 rounded-full bg-yellow-500" />
                <span className="h-3 w-3 rounded-full bg-green-500" />
                <span className="ml-2 text-xs font-mono text-slate-500">terminal@tradepulse:~/matching-engine</span>
              </div>
              <div className="flex items-center gap-6 text-xs text-slate-400 font-medium">
                <span className="flex items-center gap-1.5 text-green-400">
                  <span className="h-1.5 w-1.5 rounded-full bg-green-500 animate-ping" />
                  Engine Online
                </span>
                <span>Latency: 2.4ms</span>
                <span>TPS: 142,500/s</span>
              </div>
            </div>

            {/* MOCK ORDER BOOK & TICK PANEL */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-left">
              
              {/* ASKS (SELL ORDERS) */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Order Book - Ask Side</h4>
                <div className="space-y-1.5 font-mono text-sm">
                  <div className="flex justify-between text-red-400 bg-red-500/5 py-1 px-2.5 rounded border-l-2 border-red-500/50">
                    <span>64,258.00</span>
                    <span>1.485 BTC</span>
                  </div>
                  <div className="flex justify-between text-red-400 bg-red-500/5 py-1 px-2.5 rounded border-l-2 border-red-500/30">
                    <span>64,255.50</span>
                    <span>0.820 BTC</span>
                  </div>
                  <div className="flex justify-between text-red-400 bg-red-500/5 py-1 px-2.5 rounded border-l-2 border-red-500/10">
                    <span>64,252.10</span>
                    <span>3.125 BTC</span>
                  </div>
                </div>
              </div>

              {/* LATEST PRICE SPOTLIGHT */}
              <div className="flex flex-col items-center justify-center py-6 px-4 rounded-xl bg-slate-900/30 border border-slate-800/50 text-center">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-500">BTCUSDT Real-time</span>
                <span className="mt-3 text-4xl font-extrabold font-mono text-blue-400 animate-pulse">
                  ${tickers[0].price.toLocaleString()}
                </span>
                <span className={`mt-2 flex items-center gap-1 text-sm font-semibold ${tickers[0].change >= 0 ? "text-emerald-400" : "text-rose-400"}`}>
                  <TrendingUp className={`h-4 w-4 ${tickers[0].change < 0 && "rotate-180"}`} />
                  {tickers[0].change >= 0 ? "+" : ""}{tickers[0].change}%
                </span>
              </div>

              {/* BIDS (BUY ORDERS) */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Order Book - Bid Side</h4>
                <div className="space-y-1.5 font-mono text-sm">
                  <div className="flex justify-between text-emerald-400 bg-emerald-500/5 py-1 px-2.5 rounded border-l-2 border-emerald-500/10">
                    <span>64,248.50</span>
                    <span>2.110 BTC</span>
                  </div>
                  <div className="flex justify-between text-emerald-400 bg-emerald-500/5 py-1 px-2.5 rounded border-l-2 border-emerald-500/30">
                    <span>64,245.00</span>
                    <span>0.954 BTC</span>
                  </div>
                  <div className="flex justify-between text-emerald-400 bg-emerald-500/5 py-1 px-2.5 rounded border-l-2 border-emerald-500/50">
                    <span>64,241.20</span>
                    <span>1.845 BTC</span>
                  </div>
                </div>
              </div>

            </div>

          </div>
        </div>
      </section>

      {/* MARKETS SECTION */}
      <section id="markets" className="py-20 border-t border-slate-900 bg-slate-950/40 relative">
        <div className="mx-auto max-w-7xl px-6">
          <div className="flex flex-col md:flex-row md:items-end justify-between mb-12">
            <div>
              <h2 className="text-3xl font-extrabold text-white">Live Market Prices</h2>
              <p className="mt-2 text-slate-400">Direct streaming connection to spot markets.</p>
            </div>
            <div className="mt-4 md:mt-0 flex gap-2 border border-slate-800 bg-slate-900/30 p-1.5 rounded-lg">
              <button 
                onClick={() => setActiveTab("markets")}
                className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-all cursor-pointer ${activeTab === "markets" ? "bg-blue-600 text-white" : "text-slate-400 hover:text-white"}`}
              >
                Top Assets
              </button>
              <button 
                onClick={() => setActiveTab("tech")}
                className={`px-3 py-1.5 text-xs font-semibold rounded-md transition-all cursor-pointer ${activeTab === "tech" ? "bg-blue-600 text-white" : "text-slate-400 hover:text-white"}`}
              >
                Dual Transport Stats
              </button>
            </div>
          </div>

          {activeTab === "markets" ? (
            <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-950/70 backdrop-blur-md shadow-lg">
              <table className="w-full border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 font-medium">
                    <th className="py-4 px-6">Market</th>
                    <th className="py-4 px-6">Last Price</th>
                    <th className="py-4 px-6">24h Change</th>
                    <th className="py-4 px-6">24h High / Low</th>
                    <th className="py-4 px-6">24h Volume</th>
                    <th className="py-4 px-6 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/50 font-medium">
                  {tickers.map((ticker) => (
                    <tr key={ticker.symbol} className="hover:bg-slate-900/20 transition-colors">
                      <td className="py-4 px-6">
                        <div className="flex items-center gap-3">
                          <span className="font-bold text-white">{ticker.symbol.replace("USDT", "")}</span>
                          <span className="text-xs text-slate-500 font-semibold">/ USDT</span>
                        </div>
                      </td>
                      <td className="py-4 px-6 font-mono text-base text-white">
                        ${ticker.price.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </td>
                      <td className="py-4 px-6">
                        <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold ${ticker.change >= 0 ? "bg-emerald-500/10 text-emerald-400" : "bg-rose-500/10 text-rose-400"}`}>
                          <TrendingUp className={`h-3 w-3 ${ticker.change < 0 && "rotate-180"}`} />
                          {ticker.change >= 0 ? "+" : ""}{ticker.change}%
                        </span>
                      </td>
                      <td className="py-4 px-6 font-mono text-slate-400 text-xs">
                        H: ${ticker.high.toLocaleString()} <br />
                        L: ${ticker.low.toLocaleString()}
                      </td>
                      <td className="py-4 px-6 text-slate-400 font-mono text-xs">
                        {ticker.volume}
                      </td>
                      <td className="py-4 px-6 text-right">
                        <Link 
                          href="/register" 
                          className="inline-flex items-center gap-1 text-xs font-semibold text-blue-500 hover:text-blue-400 transition-colors"
                        >
                          Trade Now
                          <ChevronRight className="h-3 w-3" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 font-medium">
              <div className="p-6 rounded-xl border border-slate-800 bg-slate-950/70 backdrop-blur-md">
                <div className="flex items-center gap-3 text-blue-400 mb-4">
                  <Activity className="h-6 w-6" />
                  <h3 className="text-lg font-bold text-white">Binance WebSocket</h3>
                </div>
                <p className="text-sm text-slate-400 mb-4 leading-relaxed">
                  Inbound streaming connection utilizing WebFlux WebClient to process direct Binance tick feeds.
                </p>
                <div className="text-xs font-mono bg-slate-900/60 p-3 rounded-lg border border-slate-800/50 text-slate-400">
                  <div className="flex justify-between"><span>Status:</span><span className="text-green-400">CONNECTED</span></div>
                  <div className="flex justify-between mt-1"><span>Target:</span><span>wss://stream.binance.com</span></div>
                </div>
              </div>

              <div className="p-6 rounded-xl border border-slate-800 bg-slate-950/70 backdrop-blur-md">
                <div className="flex items-center gap-3 text-violet-400 mb-4">
                  <Server className="h-6 w-6" />
                  <h3 className="text-lg font-bold text-white">Kafka Message Bus</h3>
                </div>
                <p className="text-sm text-slate-400 mb-4 leading-relaxed">
                  Decoupled topic routing (`market-data`) partitioned by trading symbol for high scalability.
                </p>
                <div className="text-xs font-mono bg-slate-900/60 p-3 rounded-lg border border-slate-800/50 text-slate-400">
                  <div className="flex justify-between"><span>Active Topics:</span><span>10 Partitions</span></div>
                  <div className="flex justify-between mt-1"><span>Replication:</span><span>3x Min Sync</span></div>
                </div>
              </div>

              <div className="p-6 rounded-xl border border-slate-800 bg-slate-950/70 backdrop-blur-md">
                <div className="flex items-center gap-3 text-emerald-400 mb-4">
                  <Layers className="h-6 w-6" />
                  <h3 className="text-lg font-bold text-white">Redis Price Cache</h3>
                </div>
                <p className="text-sm text-slate-400 mb-4 leading-relaxed">
                  High-speed cache using string-based decimal prices with short TTL matching.
                </p>
                <div className="text-xs font-mono bg-slate-900/60 p-3 rounded-lg border border-slate-800/50 text-slate-400">
                  <div className="flex justify-between"><span>Price Key:</span><span>price:BTCUSDT</span></div>
                  <div className="flex justify-between mt-1"><span>TTL:</span><span>30 Seconds</span></div>
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      {/* FEATURES SECTION */}
      <section id="features" className="py-20 border-t border-slate-900">
        <div className="mx-auto max-w-7xl px-6">
          <div className="text-center max-w-3xl mx-auto mb-16 space-y-4">
            <h2 className="text-3xl font-extrabold text-white sm:text-4xl">Engineered for Performance</h2>
            <p className="text-slate-400">A cutting-edge distributed microservices system designed from the ground up.</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
            
            {/* MATCHING LATENCY */}
            <div className="p-6 rounded-2xl border border-slate-800/50 bg-slate-900/20 hover:bg-slate-900/40 transition-all group">
              <div className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 mb-5 group-hover:scale-110 transition-transform">
                <Zap className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white">Ultra-low Latency</h3>
              <p className="mt-3 text-sm text-slate-400 leading-relaxed">
                Stateful orderbooks kept entirely in matching engine memory for sub-100ms processing.
              </p>
            </div>

            {/* EVENT DRIVEN KAFKA */}
            <div className="p-6 rounded-2xl border border-slate-800/50 bg-slate-900/20 hover:bg-slate-900/40 transition-all group">
              <div className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 mb-5 group-hover:scale-110 transition-transform">
                <Layers className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white">Event-Driven Bus</h3>
              <p className="mt-3 text-sm text-slate-400 leading-relaxed">
                Decoupled transactional operations managed through high-throughput Kafka topics.
              </p>
            </div>

            {/* SECURITY (RS256 & TOTP) */}
            <div className="p-6 rounded-2xl border border-slate-800/50 bg-slate-900/20 hover:bg-slate-900/40 transition-all group">
              <div className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 mb-5 group-hover:scale-110 transition-transform">
                <ShieldCheck className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white">Secure by Design</h3>
              <p className="mt-3 text-sm text-slate-400 leading-relaxed">
                RS256 signed JWT tokens paired with database-encrypted TOTP 2FA keys.
              </p>
            </div>

            {/* POLYGLOT PERSISTENCE */}
            <div className="p-6 rounded-2xl border border-slate-800/50 bg-slate-900/20 hover:bg-slate-900/40 transition-all group">
              <div className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 mb-5 group-hover:scale-110 transition-transform">
                <Server className="h-6 w-6" />
              </div>
              <h3 className="text-lg font-bold text-white">Polyglot Storage</h3>
              <p className="mt-3 text-sm text-slate-400 leading-relaxed">
                PostgreSQL transactional logs, MongoDB audit events, and Redis real-time price caching.
              </p>
            </div>

          </div>
        </div>
      </section>

      {/* TECH STATS BANNER */}
      <section id="technology" className="py-20 bg-slate-900/30 border-t border-b border-slate-900">
        <div className="mx-auto max-w-7xl px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            <div>
              <div className="text-3xl font-extrabold text-white sm:text-4xl font-mono">100ms</div>
              <div className="mt-2 text-sm text-slate-500 font-semibold uppercase tracking-wider">Matching SLA</div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-white sm:text-4xl font-mono">10+</div>
              <div className="mt-2 text-sm text-slate-500 font-semibold uppercase tracking-wider">Kafka Partitions</div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-white sm:text-4xl font-mono">1M+</div>
              <div className="mt-2 text-sm text-slate-500 font-semibold uppercase tracking-wider">Orders / Sec</div>
            </div>
            <div>
              <div className="text-3xl font-extrabold text-white sm:text-4xl font-mono">99.99%</div>
              <div className="mt-2 text-sm text-slate-500 font-semibold uppercase tracking-wider">Uptime SLA</div>
            </div>
          </div>
        </div>
      </section>

      {/* CALL TO ACTION */}
      <section className="py-20 relative overflow-hidden">
        <div className="mx-auto max-w-5xl px-6">
          <div className="rounded-3xl border border-blue-500/30 bg-gradient-to-r from-blue-900/40 to-indigo-900/40 p-8 md:p-12 text-center relative overflow-hidden backdrop-blur-md">
            
            {/* DECORATIVE LIGHT SOURCE */}
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[300px] w-[300px] bg-blue-500/20 rounded-full blur-3xl -z-10" />

            <div className="max-w-2xl mx-auto space-y-6">
              <h2 className="text-3xl font-extrabold text-white sm:text-4xl">Ready to Start Trading?</h2>
              <p className="text-slate-300">
                Create a demo account now to experience real-time market data streaming and instant execution.
              </p>
              <div className="pt-4">
                <Link
                  href="/register"
                  className="inline-flex items-center gap-2 rounded-xl bg-white px-8 py-4 font-bold text-slate-950 hover:bg-slate-100 transition-all shadow-xl shadow-white/10 cursor-pointer"
                >
                  Create Your Account
                  <ArrowRight className="h-4 w-4 text-slate-950" />
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="border-t border-slate-900 py-12 bg-slate-950/80">
        <div className="mx-auto max-w-7xl px-6 flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <div className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 font-bold text-white shadow-md shadow-blue-500/20">
              TP
            </div>
            <span className="text-lg font-bold tracking-tight text-white">
              Trade<span className="text-blue-500">Pulse</span>
            </span>
          </div>

          <p className="text-xs text-slate-500 font-medium">
            &copy; {new Date().getFullYear()} TradePulse Exchange. All rights reserved.
          </p>

          <div className="flex gap-6 text-xs text-slate-500 font-semibold">
            <a href="#" className="hover:text-slate-400 transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-slate-400 transition-colors">Terms of Service</a>
          </div>
        </div>
      </footer>

    </div>
  );
}
