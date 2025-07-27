
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Stethoscope, Siren, TriangleAlert } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function HospitalsDashboard() {
  const { toast } = useToast();
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      accidentAlerts: "0",
      highTrauma: "0",
      erActivations: "0"
  });

  useEffect(() => {
    const fetchHospitalData = async () => {
      try {
        const hospitalAlerts = await getAlerts("Hospitals");
        setAlerts(hospitalAlerts);

        const accidentAlertsCount = hospitalAlerts.filter(a => a.type.toLowerCase().includes('accident') || a.type.toLowerCase().includes('collision')).length;
        const highTraumaCount = hospitalAlerts.filter(a => a.severity === 'High' || a.severity === 'Critical').length;
        const erActivationsCount = hospitalAlerts.filter(a => a.status === 'ER Activated').length;

        setStats({
            accidentAlerts: accidentAlertsCount.toString(),
            highTrauma: highTraumaCount.toString(),
            erActivations: erActivationsCount.toString(),
        });

      } catch (error) {
        console.error("Failed to fetch hospital data:", error);
      }
    };
    fetchHospitalData();
  }, []);

  const handleActivateER = () => {
    toast({
        title: "ER Protocol Activated",
        description: "St. John's Hospital has been notified to prepare for incoming trauma patients from Incident #12A.",
        variant: "destructive",
    });
  }

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Hospitals Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Nearby Accident Alerts" icon={Siren} value={stats.accidentAlerts} />
        <AnalyticsCard title="High Trauma Tags" icon={TriangleAlert} value={stats.highTrauma} />
        <AnalyticsCard title="ER Activations" icon={Stethoscope} value={stats.erActivations} />
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-7">
        <div className="lg:col-span-4 grid grid-cols-1 gap-4">
            <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/hospitals/alerts" />
        </div>
        <div className="lg:col-span-3 grid grid-cols-1 gap-4">
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2"><Siren className="text-destructive animate-pulse" /> ER Activation</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                    <p className="text-sm text-muted-foreground">Simulate ER activation for incoming high-trauma patients.</p>
                    <p className="text-sm">Incident #12A tagged as <strong>Severe Trauma</strong>.</p>
                    <Button className="w-full" variant="destructive" onClick={handleActivateER}>Activate "ER-Ready" Protocol</Button>
                </CardContent>
            </Card>
        </div>
      </div>
    </div>
  );
}
