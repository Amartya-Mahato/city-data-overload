
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
import { AlertTriangle, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { type Alert } from "@/lib/data";
import { getAllAlerts, escalateAlert } from "@/services/firestore";
import { Skeleton } from "@/components/ui/skeleton";


export default function AllAlertsPage() {
  const [alerts, setAlerts] = React.useState<Alert[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedAlert, setSelectedAlert] = React.useState<Alert | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const [isEscalateOpen, setIsEscalateOpen] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchAlerts = async () => {
      setIsLoading(true);
      try {
        const fetchedAlerts = await getAllAlerts();
        setAlerts(fetchedAlerts);
      } catch (error) {
        console.error("Failed to fetch alerts:", error);
        toast({
          title: "Error",
          description: "Could not fetch alerts from the database. Please check console for details.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };

    fetchAlerts();
  }, [toast]);


  const handleEscalate = (alert: Alert) => {
    setSelectedAlert(alert);
    setIsEscalateOpen(true);
  };
  
  const handleViewDetails = (alert: Alert) => {
    setSelectedAlert(alert);
    setIsDetailsOpen(true);
  };

  const confirmEscalate = async () => {
    if (selectedAlert) {
       try {
        await escalateAlert(selectedAlert.id);
        
        toast({
          title: "Alert Escalated",
          description: `Alert #${selectedAlert.id} has been escalated to the ${selectedAlert.department} department.`,
        });
        
        const newAlerts = alerts.map(a => a.id === selectedAlert.id ? { ...a, isEscalated: true, status: 'Escalated' } : a)
        setAlerts(newAlerts);

      } catch (error) {
         console.error("Failed to escalate alert:", error);
        toast({
          title: "Error",
          description: "Could not escalate the alert.",
          variant: "destructive",
        });
      }
    }
    setIsEscalateOpen(false);
    setSelectedAlert(null);
  };


  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">All Department Alerts</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><AlertTriangle /> System-Wide Alert Log</CardTitle>
            <CardDescription>A comprehensive log of all alerts from every department, loaded from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Alert ID</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Urgency</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  alerts.map((alert) => (
                    <TableRow key={alert.id}>
                      <TableCell className="font-medium">{alert.id}</TableCell>
                      <TableCell>{alert.department}</TableCell>
                      <TableCell>{alert.type}</TableCell>
                      <TableCell>{alert.location}</TableCell>
                      <TableCell>
                        <Badge variant={alert.urgency === 'Critical' || alert.urgency === 'High' ? 'destructive' : alert.urgency === 'Medium' ? 'default' : 'secondary'}>
                          {alert.urgency}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={alert.isEscalated ? 'destructive' : (alert.status === 'New' || alert.status === 'Open' ? 'default' : 'secondary')}>
                          {alert.isEscalated ? 'Escalated' : alert.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                         <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0" disabled={alert.isEscalated}>
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleViewDetails(alert)}>
                              View Details
                            </DropdownMenuItem>
                            {!alert.isEscalated && (
                              <DropdownMenuItem onClick={() => handleEscalate(alert)}>
                                Escalate to Department
                              </DropdownMenuItem>
                            )}
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
            <DialogTitle>Alert Details: {selectedAlert?.id}</DialogTitle>
            <DialogDescription>
              A detailed view of the alert information.
            </DialogDescription>
          </DialogHeader>
          {selectedAlert && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Department</h4>
                <p>{selectedAlert.department}</p>
              </div>
              <div>
                <h4 className="font-semibold">Type</h4>
                <p>{selectedAlert.type}</p>
              </div>
              <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedAlert.location}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedAlert.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
      
      {/* Escalate Confirmation Dialog */}
      <AlertDialog open={isEscalateOpen} onOpenChange={setIsEscalateOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you sure you want to escalate?</AlertDialogTitle>
            <AlertDialogDescription>
              This action will notify the <strong>{selectedAlert?.department}</strong> department about alert <strong>{selectedAlert?.id}</strong>. This cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={confirmEscalate}>Confirm Escalation</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
