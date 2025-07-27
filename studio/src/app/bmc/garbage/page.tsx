
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
import { Trash2, MoreHorizontal } from "lucide-react";
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
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useToast } from "@/hooks/use-toast";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { type Alert } from "@/lib/data";
import { getAlerts, updateAlertStatus } from "@/services/firestore";
import { Skeleton } from "@/components/ui/skeleton";

type Ticket = Alert & {
    status: "Open" | "In Progress" | "Resolved";
};

export default function GarbageTicketsPage() {
  const [tickets, setTickets] = React.useState<Ticket[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedTicket, setSelectedTicket] = React.useState<Ticket | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const [isUpdateOpen, setIsUpdateOpen] = React.useState(false);
  const [newStatus, setNewStatus] = React.useState<Ticket['status'] | null>(null);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchTickets = async () => {
      try {
        const fetchedAlerts = await getAlerts('BMC');
        const bmcTickets = fetchedAlerts
          .filter(a => a.type === "Garbage" || a.type === "Sewage")
          .map(a => ({...a, status: a.status as Ticket['status']}));
        setTickets(bmcTickets);
      } catch (error) {
        console.error("Failed to fetch tickets:", error);
        toast({
          title: "Error",
          description: "Could not fetch tickets from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchTickets();
  }, [toast]);

  const handleViewDetails = (ticket: Ticket) => {
    setSelectedTicket(ticket);
    setIsDetailsOpen(true);
  };

  const handleUpdateStatus = (ticket: Ticket) => {
    setSelectedTicket(ticket);
    setNewStatus(ticket.status);
    setIsUpdateOpen(true);
  };

  const confirmUpdateStatus = async () => {
    if (selectedTicket && newStatus) {
      try {
        await updateAlertStatus(selectedTicket.id, newStatus);
        
        setTickets(prevTickets =>
          prevTickets.map(t =>
            t.id === selectedTicket.id ? { ...t, status: newStatus } : t
          )
        );

        toast({
          title: "Status Updated",
          description: `Ticket #${selectedTicket.id} status has been updated to "${newStatus}".`,
        });
      } catch (error) {
        console.error("Failed to update status:", error);
        toast({
          title: "Error",
          description: "Could not update ticket status.",
          variant: "destructive",
        });
      }
    }
    setIsUpdateOpen(false);
    setSelectedTicket(null);
  };

  const getStatusBadgeVariant = (status: Ticket['status']) => {
    switch (status) {
      case 'Resolved':
        return 'secondary';
      case 'Open':
        return 'destructive';
      case 'In Progress':
        return 'default';
      default:
        return 'default';
    }
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Garbage & Sewage Tickets</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Trash2 /> Active Garbage & Sewage Tickets</CardTitle>
            <CardDescription>A log of all collection and disposal tickets from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Ticket ID</TableHead>
                   <TableHead>Type</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date Reported</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  tickets.map((ticket) => (
                    <TableRow key={ticket.id}>
                      <TableCell className="font-medium">{ticket.id}</TableCell>
                      <TableCell>{ticket.type}</TableCell>
                      <TableCell>{ticket.location}</TableCell>
                      <TableCell>
                        <Badge variant={getStatusBadgeVariant(ticket.status)}>
                          {ticket.status}
                        </Badge>
                      </TableCell>
                      <TableCell>{ticket.date}</TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleViewDetails(ticket)}>View Details</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleUpdateStatus(ticket)}>Update Status</DropdownMenuItem>
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
            <DialogTitle>Ticket Details: {selectedTicket?.id}</DialogTitle>
            <DialogDescription>
              A detailed view of the ticket information.
            </DialogDescription>
          </DialogHeader>
          {selectedTicket && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedTicket.location}</p>
              </div>
               <div>
                <h4 className="font-semibold">Date Reported</h4>
                <p>{selectedTicket.date}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedTicket.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
      
      {/* Update Status Dialog */}
      <AlertDialog open={isUpdateOpen} onOpenChange={setIsUpdateOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Update Ticket Status</AlertDialogTitle>
            <AlertDialogDescription>
              Select the new status for ticket <strong>{selectedTicket?.id}</strong>.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="py-4">
            <Select onValueChange={(value: Ticket['status']) => setNewStatus(value)} defaultValue={selectedTicket?.status}>
                <SelectTrigger>
                    <SelectValue placeholder="Select a status" />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="Open">Open</SelectItem>
                    <SelectItem value="In Progress">In Progress</SelectItem>
                    <SelectItem value="Resolved">Resolved</SelectItem>
                </SelectContent>
            </Select>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={confirmUpdateStatus}>Update</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
