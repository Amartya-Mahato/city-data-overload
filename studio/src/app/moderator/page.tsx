
"use client";

import { useState, useEffect } from 'react';
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import Image from "next/image";
import { FileQuestion, Check, X, Languages, Sparkles } from "lucide-react";
import { suggestContentCategories } from '@/ai/flows/suggest-content-categories';
import { Skeleton } from '@/components/ui/skeleton';
import { AlertFeed } from '@/components/dashboards/alert-feed';
import { getAlerts } from '@/services/firestore';
import type { Alert } from '@/lib/data';
import dynamic from 'next/dynamic';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function ModeratorDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      pendingQueue: "0",
      approvedToday: "152", // Static for now
      rejectedToday: "34" // Static for now
  });
  
  const [categories, setCategories] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [contentToAnalyze, setContentToAnalyze] = useState(
    "Huge crowd gathering near Town Hall. Looks like a protest."
  );

  useEffect(() => {
    const fetchModeratorData = async () => {
        try {
            const moderatorAlerts = await getAlerts("Moderator");
            setAlerts(moderatorAlerts);

            const pendingQueueCount = moderatorAlerts.filter(a => a.status === 'Pending').length;
            
            setStats(prevStats => ({
                ...prevStats,
                pendingQueue: pendingQueueCount.toString(),
            }));

        } catch (error) {
            console.error("Failed to fetch moderator data:", error);
        }
    };
    fetchModeratorData();
  }, []);

  useEffect(() => {
    const getCategories = async () => {
      if (contentToAnalyze) {
        setIsLoading(true);
        try {
          const result = await suggestContentCategories({ content: contentToAnalyze });
          setCategories(result.categories);
        } catch (error) {
          console.error("Error fetching categories:", error);
          setCategories(['Error fetching suggestions']);
        } finally {
          setIsLoading(false);
        }
      }
    };
    getCategories();
  }, [contentToAnalyze]);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Moderator Panel</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Pending Queue" icon={FileQuestion} value={stats.pendingQueue} />
        <AnalyticsCard title="Approved Today" icon={Check} value={stats.approvedToday} />
        <AnalyticsCard title="Rejected Today" icon={X} value={stats.rejectedToday} />
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-7">
        <div className="lg:col-span-4 grid grid-cols-1 gap-4">
            <Card>
                <CardHeader>
                    <CardTitle>Unverified Content Queue</CardTitle>
                    <CardDescription>Review user-submitted content for approval.</CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="rounded-lg border bg-card text-card-foreground shadow-sm p-4 space-y-4">
                        <div className="aspect-video relative w-full overflow-hidden rounded-md bg-muted">
                            <Image 
                                src="https://placehold.co/600x400.png"
                                alt="User submission"
                                fill
                                className="object-cover"
                                data-ai-hint="street protest"
                            />
                        </div>
                        <div>
                            <p className="text-sm text-muted-foreground">"{contentToAnalyze}"</p>
                            <div className="flex items-center gap-2 mt-2">
                                <Badge variant="outline" className="flex items-center gap-1"><Languages size={14}/> Language: English</Badge>
                                <Badge variant="destructive">Abuse Filter: Negative</Badge>
                            </div>
                        </div>
                        <div className="flex justify-end gap-2">
                            <Button variant="outline">Route for Review</Button>
                            <Button variant="destructive">Reject</Button>
                            <Button>Approve</Button>
                        </div>
                    </div>
                </CardContent>
            </Card>
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2"><Sparkles className="text-primary" /> Gemini Category Suggestions</CardTitle>
                    <CardDescription>AI-powered suggestions for the content on the left.</CardDescription>
                </CardHeader>
                <CardContent>
                    <p className="text-sm text-muted-foreground mb-2">Based on content analysis, suggest routing to:</p>
                    <div className="flex flex-wrap gap-2">
                        {isLoading ? (
                          <>
                            <Skeleton className="h-6 w-20 rounded-full" />
                            <Skeleton className="h-6 w-24 rounded-full" />
                            <Skeleton className="h-6 w-16 rounded-full" />
                          </>
                        ) : (
                          categories.map((category, index) => (
                            <Badge key={index}>{category}</Badge>
                          ))
                        )}
                    </div>
                </CardContent>
            </Card>
        </div>
        <div className="lg:col-span-3">
            <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/moderator/queue" />
        </div>
      </div>
    </div>
  );
}
