
"use client";

import * as React from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { List, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import Image from "next/image";
import { getUserReportedEvents } from "@/services/firestore";
import { Skeleton } from "@/components/ui/skeleton";

type Content = {
  id: string;
  type: "Image" | "Text" | "Video";
  submitter: string;
  snippet: string;
  date: string;
  status: "Pending" | "Approved" | "Rejected";
  fullContent: string;
  imageUrl?: string;
};

export default function PendingQueuePage() {
  const [queue, setQueue] = React.useState<Content[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedContent, setSelectedContent] = React.useState<Content | null>(null);
  const [isReviewOpen, setIsReviewOpen] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchQueue = async () => {
        setIsLoading(true);
        try {
            const fetchedEvents = await getUserReportedEvents();
            const typedEvents = fetchedEvents.map(e => ({
                ...e,
                status: e.status || "Pending",
            })) as Content[];
            setQueue(typedEvents);
        } catch (error) {
            console.error("Failed to fetch user reported events:", error);
            toast({
                title: "Error",
                description: "Could not fetch user reported events from the database.",
                variant: "destructive",
            });
        } finally {
            setIsLoading(false);
        }
    };
    fetchQueue();
  }, [toast]);


  const handleReview = (item: Content) => {
    setSelectedContent(item);
    setIsReviewOpen(true);
  };

  const handleAction = (id: string, action: "Approve" | "Reject" | "Route") => {
    const item = queue.find(i => i.id === id);
    if (item) {
      toast({
        title: `Content ${action === 'Route' ? 'Routed' : action + 'd'}`,
        description: `Content ID #${id} has been ${action === 'Route' ? 'routed to the appropriate department' : action.toLowerCase() + 'd'}.`,
      });
      // In a real app, this would also update the item's status in the database
      setQueue(prevQueue => prevQueue.filter(i => i.id !== id));
    }
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Pending Queue</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><List /> Content Moderation Queue</CardTitle>
            <CardDescription>User-submitted content awaiting review from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Content ID</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Submitter</TableHead>
                  <TableHead>Snippet</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  queue.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.id}</TableCell>
                      <TableCell>{item.type}</TableCell>
                      <TableCell>{item.submitter}</TableCell>
                      <TableCell className="truncate max-w-xs">{item.snippet}</TableCell>
                      <TableCell>{item.date}</TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleReview(item)}>Review Full Content</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleAction(item.id, 'Approve')}>Approve</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleAction(item.id, 'Reject')}>Reject</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleAction(item.id, 'Route')}>Route to Department</DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <Dialog open={isReviewOpen} onOpenChange={setIsReviewOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Review Content: {selectedContent?.id}</DialogTitle>
            <DialogDescription>
              Review the full user-submitted content and take action.
            </DialogDescription>
          </DialogHeader>
          {selectedContent && (
            <div className="space-y-4 py-4">
              {selectedContent.imageUrl && (
                <div className="aspect-video relative w-full overflow-hidden rounded-md bg-muted">
                  <Image
                    src={selectedContent.imageUrl}
                    alt="User submission"
                    fill
                    className="object-cover"
                    data-ai-hint="user submission"
                  />
                </div>
              )}
              <div>
                <h4 className="font-semibold">Full Content / Description</h4>
                <p className="text-muted-foreground">{selectedContent.fullContent}</p>
              </div>
              <div className="flex justify-end gap-2 pt-4">
                <Button variant="outline" onClick={() => { handleAction(selectedContent.id, 'Route'); setIsReviewOpen(false); }}>Route to Department</Button>
                <Button variant="destructive" onClick={() => { handleAction(selectedContent.id, 'Reject'); setIsReviewOpen(false); }}>Reject</Button>
                <Button onClick={() => { handleAction(selectedContent.id, 'Approve'); setIsReviewOpen(false); }}>Approve</Button>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
