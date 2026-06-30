"use client"; // Required for client-side React features (state, routers)

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Eye, EyeOff, Lock, Mail, ShieldCheck, RefreshCw } from "lucide-react";

export default function LoginPage() {
  const router = useRouter(); // Instantiates Next.js client-side router for redirects

  // ============================================================================
  // REACT STATES
  // ============================================================================
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [showPassword, setShowPassword] = useState(false); // Controls password visibility toggle
  const [loading, setLoading] = useState(false); // Controls loading/spinner animation
  const [error, setError] = useState<string | null>(null); // Stores submission error messages
  const [successMessage, setSuccessMessage] = useState<string | null>(null); // Stores success messages

  useEffect(() => {
    if (typeof window !== "undefined") {
      const params = new URLSearchParams(window.location.search);
      if (params.get("registered") === "true") {
        setSuccessMessage("Registration successful! Please sign in with your credentials.");
      }
    }
  }, []);

  // Handles form submission event
  const handleSubmit = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault(); // Prevent default browser reload on form submit
    setLoading(true);
    setError(null);

    try {
      // POST authentication request to the local Next.js BFF API route
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email, password, totpCode }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || "Authentication failed.");
      }

      // Redirect user to the trading dashboard upon successful login
      router.push("/trade");
    } catch (err: any) {
      setError(err.message || "An unexpected network error occurred.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-radial from-[#1e293b] to-[#0f172a] px-4 py-12 text-slate-100">
      <div className="w-full max-w-md space-y-8 rounded-2xl border border-slate-700/50 bg-slate-900/80 p-8 shadow-2xl backdrop-blur-xl">
        
        {/* LOGO & TITLE */}
        <div className="text-center">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-xl bg-blue-600 font-bold text-2xl text-white shadow-lg shadow-blue-500/30">
            TP
          </div>
          <h2 className="mt-6 text-3xl font-extrabold tracking-tight text-white">
            TradePulse Exchange
          </h2>
          <p className="mt-2 text-sm text-slate-400">
            Sign in to start trading
          </p>
        </div>

        {/* SUCCESS BOX */}
        {successMessage && (
          <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-400">
            {successMessage}
          </div>
        )}

        {/* ERROR BOX */}
        {error && (
          <div className="rounded-lg border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-400 animate-pulse">
            {error}
          </div>
        )}

        {/* SIGN IN FORM */}
        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <div className="space-y-4 rounded-md shadow-sm">
            
            {/* EMAIL FIELD */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-slate-300">
                Email Address
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                  <Mail className="h-5 w-5" />
                </div>
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="username" // Enables password managers to autofill username
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950 py-2.5 pl-10 pr-3 text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-hidden focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="name@example.com"
                />
              </div>
            </div>

            {/* PASSWORD FIELD */}
            <div>
              <div className="flex items-center justify-between">
                <label htmlFor="current-password" className="block text-sm font-medium text-slate-300">
                  Password
                </label>
                <a href="#" className="text-xs text-blue-500 hover:text-blue-400 transition-colors">
                  Forgot password?
                </a>
              </div>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                  <Lock className="h-5 w-5" />
                </div>
                <input
                  id="current-password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password" // Enables browser to autofill current password
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950 py-2.5 pl-10 pr-10 text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-hidden focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="••••••••"
                />
                {/* SHOW/HIDE PASSWORD TOGGLE */}
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 flex items-center pr-3 text-slate-400 hover:text-slate-200 transition-colors"
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            {/* TOTP 2FA FIELD */}
            <div>
              <label htmlFor="otp" className="block text-sm font-medium text-slate-300">
                2FA Verification Code (Optional)
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                  <ShieldCheck className="h-5 w-5" />
                </div>
                <input
                  id="otp"
                  name="otp"
                  type="text"
                  inputMode="numeric" // Triggers numeric keyboard on mobile devices
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950 py-2.5 pl-10 pr-3 text-slate-200 placeholder-slate-500 focus:border-blue-500 focus:outline-hidden focus:ring-2 focus:ring-blue-500/20 text-sm"
                  placeholder="e.g. 123456"
                  maxLength={6}
                />
              </div>
            </div>

          </div>

          {/* SUBMIT BUTTON */}
          <div>
            <button
              type="submit"
              disabled={loading} // Disables button during pending requests to prevent duplicate submissions
              className="group relative flex w-full justify-center rounded-lg bg-blue-600 py-3 px-4 text-sm font-medium text-white hover:bg-blue-500 focus:outline-hidden focus:ring-2 focus:ring-blue-500/50 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-blue-500/20 cursor-pointer"
            >
              {loading ? (
                <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
              ) : (
                "Sign In"
              )}
            </button>
          </div>

        </form>

        {/* REGISTRATION REDIRECT */}
        <p className="text-center text-xs text-slate-500">
          Don't have an account?{" "}
          <Link href="/register" className="font-semibold text-blue-500 hover:text-blue-400 transition-colors">
            Register now
          </Link>
        </p>

      </div>
    </div>
  );
}
