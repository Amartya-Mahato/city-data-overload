
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Car, TrafficCone, TreeDeciduous, Bot } from "lucide-react";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function TrafficDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      highCongestion: "0",
      activeReroutes: "0",
      obstructions: "0"
  });

  useEffect(() => {
    const fetchTrafficData = async () => {
      try {
        const trafficAlerts = await getAlerts("Traffic");
        setAlerts(trafficAlerts);

        const highCongestionCount = trafficAlerts.filter(a => a.type === 'Congestion' && a.urgency === 'High').length;
        const activeReroutesCount = trafficAlerts.filter(a => a.type === 'Reroute').length;
        const obstructionsCount = trafficAlerts.filter(a => a.type === 'Obstruction').length;

        setStats({
            highCongestion: highCongestionCount.toString(),
            activeReroutes: activeReroutesCount.toString(),
            obstructions: obstructionsCount.toString(),
        });

      } catch (error) {
        console.error("Failed to fetch traffic data:", error);
      }
    };
    fetchTrafficData();
  }, []);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Traffic Control Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="High Congestion Zones" icon={Car} value={stats.highCongestion} />
        <AnalyticsCard title="Active Reroutes" icon={TrafficCone} value={stats.activeReroutes} />
        <AnalyticsCard title="Obstruction Incidents" icon={TreeDeciduous} value={stats.obstructions} />
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-7">
        <div className="lg:col-span-4 grid grid-cols-1 gap-4">
            <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/traffic/congestion" />
        </div>
        <div className="lg:col-span-3 grid grid-cols-1 gap-4">
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2"><Bot /> Reroute Suggestions</CardTitle>
                </CardHeader>
                <CardContent>
                    <p className="text-sm font-bold text-primary">For Silk Board Gridlock:</p>
                    <p className="text-sm text-muted-foreground">Gemini-summarized suggestion:</p>
                    <blockquote className="mt-2 border-l-2 pl-4 italic">
                    "Divert traffic from Outer Ring Road towards BTM Layout. Use service roads to bypass the main junction. Advise commuters heading to HSR to take the Agara flyover."
                    </blockquote>
                </CardContent>
            </Card>
        </div>
      </div>
    </div>
  );
}
