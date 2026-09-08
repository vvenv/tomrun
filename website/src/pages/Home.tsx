import { Navbar } from "@/components/Navbar";
import { Hero } from "@/components/Hero";
import { Features } from "@/components/Features";
import { Worlds } from "@/components/Worlds";
import { Museum } from "@/components/Museum";
import { Workflow } from "@/components/Workflow";
import { Leaderboard } from "@/components/Leaderboard";
import { Download } from "@/components/Download";
import { Faq } from "@/components/Faq";
import { Footer } from "@/components/Footer";
import { JsonLd } from "@/components/JsonLd";

export default function Home() {
  return (
    <div className="relative min-h-screen overflow-x-hidden">
      <JsonLd />
      <Navbar />
      <main>
        <Hero />
        <Features />
        <Worlds />
        <Museum />
        <Workflow />
        <Leaderboard />
        <Download />
        <Faq />
      </main>
      <Footer />
    </div>
  );
}
