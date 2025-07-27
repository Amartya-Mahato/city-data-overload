"use client";

import React, { useState, useEffect } from 'react';
import {
  AlertTriangle,
  CheckCircle,
  Shield,
  Users,
  Upload,
} from 'lucide-react';
import { AlertFeed } from '@/components/dashboards/alert-feed';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import { uploadInitialData, getAllAlerts } from '@/services/firestore';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { Alert } from '@/lib/data';
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function SuperAdminDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const { toast } = useToast();
  const [stats, setStats] = useState({
    totalAlerts: '0',
    activeCases: '0',
    resolvedCases: '0',
    systemHealth: '99.8%',
  });

  useEffect(() => {
    const fetchAlerts = async () => {
      try {
        const fetchedAlerts = await getAllAlerts();
        setAlerts(fetchedAlerts);

        const activeCasesCount = fetchedAlerts.filter(a => a.status !== 'Resolved' && a.status !== 'Closed').length;
        const resolvedCasesCount = fetchedAlerts.filter(a => a.status === 'Resolved' || a.status === 'Closed').length;
        
        setStats(prevStats => ({
          ...prevStats,
          totalAlerts: fetchedAlerts.length.toString(),
          activeCases: activeCasesCount.toString(),
          resolvedCases: resolvedCasesCount.toString(),
        }));

      } catch (error) {
        console.error("Failed to fetch alerts:", error);
        toast({
          title: "Error",
          description: "Could not fetch dashboard data from Firestore.",
          variant: "destructive",
        });
      }
    }
    fetchAlerts();
  }, [toast]);

  const handleDataUpload = async () => {
    try {
      await uploadInitialData();
      toast({
        title: 'Success',
        description: 'Initial mock data has been uploaded to Firestore.',
      });
      const fetchedAlerts = await getAllAlerts();
      setAlerts(fetchedAlerts);

    } catch (error) {
      console.error("Error uploading data: ", error);
      toast({
        title: 'Error',
        description: 'Could not upload data to Firestore. Check the console for details.',
        variant: 'destructive',
      });
    }
  };
  
  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <div className="flex items-center justify-between space-y-2">
        <h2 className="text-3xl font-bold tracking-tight">Super Admin Dashboard</h2>
      </div>
       <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <AnalyticsCard
          title="Total Alerts"
          icon={AlertTriangle}
          value={stats.totalAlerts}
          change="+5.2% from last month"
        />
        <AnalyticsCard
          title="Active Cases"
          icon={Users}
          value={stats.activeCases}
          change="+3.1% from last month"
        />
        <AnalyticsCard
          title="Resolved Cases"
          icon={CheckCircle}
          value={stats.resolvedCases}
          change="+1.9% from last month"
        />
        <AnalyticsCard
          title="System Health"
          icon={Shield}
          value={stats.systemHealth}
          change="All services operational"
        />
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-7">
        <div className="lg:col-span-4">
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2"><Upload /> Database Setup</CardTitle>
                    <CardDescription>One-time action to populate your Firestore database with the initial set of mock alerts.</CardDescription>
                </CardHeader>
                <CardContent>
                    <Button onClick={handleDataUpload} className="w-full">Upload Initial Data</Button>
                </CardContent>
            </Card>
        </div>
        <div className="lg:col-span-3">
          <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/super-admin/alerts" />
        </div>
      </div>
    </div>
  );
}
