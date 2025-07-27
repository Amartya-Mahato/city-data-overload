
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
import { getEvents } from "@/services/firestore";
import { useToast } from "@/hooks/use-toast";
import { Skeleton } from "@/components/ui/skeleton";

type CommunityEvent = {
  id: string;
  name: string;
  type: string;
  date: string;
  status: "Upcoming" | "Completed" | "Cancelled";
  description: string;
};

export default function EventsListPage() {
  const [events, setEvents] = React.useState<CommunityEvent[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedEvent, setSelectedEvent] = React.useState<CommunityEvent | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchEvents = async () => {
      setIsLoading(true);
      try {
        const fetchedEvents = await getEvents();
        // Ensure that fetched data conforms to the CommunityEvent type, especially the status
        const typedEvents = fetchedEvents.map(e => ({
            ...e,
            status: e.status || "Upcoming", // Provide a default status if none exists
        })) as CommunityEvent[];
        setEvents(typedEvents);
      } catch (error) {
        console.error("Failed to fetch events:", error);
        toast({
          title: "Error",
          description: "Could not fetch events from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchEvents();
  }, [toast]);

  const handleViewDetails = (event: CommunityEvent) => {
    setSelectedEvent(event);
    setIsDetailsOpen(true);
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Community Events</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><List /> Upcoming and Past Events</CardTitle>
            <CardDescription>A list of all community events from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Event ID</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-40" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  events.map((event) => (
                    <TableRow key={event.id}>
                      <TableCell className="font-medium">{event.id}</TableCell>
                      <TableCell>{event.name}</TableCell>
                      <TableCell>{event.type}</TableCell>
                      <TableCell>{event.date}</TableCell>
                      <TableCell>
                        <Badge variant={event.status === 'Upcoming' ? 'default' : 'secondary'}>
                          {event.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleViewDetails(event)}>View Details</DropdownMenuItem>
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

      {/* View Details Dialog */}
      <Dialog open={isDetailsOpen} onOpenChange={setIsDetailsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Event Details: {selectedEvent?.name}</DialogTitle>
            <DialogDescription>
              A detailed view of the event information.
            </DialogDescription>
          </DialogHeader>
          {selectedEvent && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Event ID</h4>
                <p>{selectedEvent.id}</p>
              </div>
               <div>
                <h4 className="font-semibold">Type</h4>
                <p>{selectedEvent.type}</p>
              </div>
               <div>
                <h4 className="font-semibold">Date</h4>
                <p>{selectedEvent.date}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedEvent.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
