import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { TrendingUp, Gauge, ExternalLink, Loader2 } from "lucide-react";
import { useToast } from "@/components/ui/use-toast";

interface PerformanceMetric {
  name: string;
  score: number;
}

const LighthouseWidget = () => {
  const [metrics, setMetrics] = useState<PerformanceMetric[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [reportUrl, setReportUrl] = useState<string>("");
  const { toast } = useToast();

  useEffect(() => {
    const fetchLighthouseData = async () => {
      try {
        setIsLoading(true);
        const websiteUrl = encodeURIComponent("https://demo.lovable.dev");
        // Use environment variable for API key
        const apiKey = import.meta.env.VITE_PAGEVITALS_API_KEY;
        const apiUrl = `https://www.googleapis.com/pagespeedonline/v5/runPagespeed?url=${websiteUrl}&strategy=mobile${apiKey ? `&key=${apiKey}` : ""}`;

        const response = await fetch(apiUrl);
        const data = await response.json();

        if (data.error) {
          throw new Error(data.error.message || "Failed to fetch Lighthouse data");
        }

        const categories = data.lighthouseResult.categories;
        const fetchedMetrics: PerformanceMetric[] = [
          { name: "Performance", score: Math.round(categories.performance.score * 100) },
          { name: "Accessibility", score: Math.round(categories.accessibility.score * 100) },
          { name: "Best Practices", score: Math.round(categories["best-practices"].score * 100) },
          { name: "SEO", score: Math.round(categories.seo.score * 100) },
        ];

        setMetrics(fetchedMetrics);
        setReportUrl(`https://pagespeed.web.dev/report?url=${websiteUrl}`);
        setIsLoading(false);
      } catch (error) {
        console.error("Failed to fetch Lighthouse data:", error);
        toast({
          title: "Error fetching Lighthouse data",
          description: "Unable to load performance metrics. Using fallback data.",
          variant: "destructive",
        });
        setMetrics([
          { name: "Performance", score: 96 },
          { name: "Accessibility", score: 98 },
          { name: "Best Practices", score: 100 },
          { name: "SEO", score: 100 },
        ]);
        setReportUrl("https://pagespeed.web.dev/");
        setIsLoading(false);
      }
    };

    fetchLighthouseData();
  }, [toast]);

  const getScoreColor = (score: number) => {
    if (score >= 90) return "bg-green-500";
    if (score >= 75) return "bg-yellow-500";
    return "bg-red-500";
  };

  return (
    <div className="w-full max-w-screen-lg mx-auto px-4 py-8">
      <Card className="w-full md:w-96 mx-auto bg-[#101630] border border-[#1e293b] shadow-lg">
        <CardContent className="p-4">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Gauge className="text-accent" size={18} />
              <h3 className="font-medium text-white text-sm">Lighthouse Metrics</h3>
            </div>
            <TrendingUp className="text-green-400" size={16} />
          </div>

          {isLoading ? (
            <div className="h-32 flex items-center justify-center">
              <Loader2 className="h-8 w-8 text-accent animate-spin" />
              <span className="ml-2 text-sm text-gray-300">Loading metrics...</span>
            </div>
          ) : (
            <div className="space-y-3">
              {metrics.map((metric) => (
                <div key={metric.name} className="flex items-center justify-between">
                  <span className="text-xs text-gray-300">{metric.name}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-white">{metric.score}</span>
                    <div className={`h-2 w-2 rounded-full ${getScoreColor(metric.score)}`}></div>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="mt-4 pt-3 border-t border-[#1e293b] flex flex-col items-center">
            <p className="text-[10px] text-gray-400">Last updated {new Date().toLocaleDateString()}</p>
            {!isLoading && (
              <a
                href={reportUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-2 flex items-center gap-1 text-xs text-accent hover:text-accent/80 transition-colors"
              >
                View full report <ExternalLink size={12} />
              </a>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default LighthouseWidget;
