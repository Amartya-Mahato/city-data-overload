
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
import { Wrench, MoreHorizontal } from "lucide-react";
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

type Report = Alert & {
  severity: "Low" | "Medium" | "High";
  status: "New" | "Work in Progress" | "Resolved";
};

export default function PotholeReportsPage() {
  const [reports, setReports] = React.useState<Report[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedReport, setSelectedReport] = React.useState<Report | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const [isUpdateOpen, setIsUpdateOpen] = React.useState(false);
  const [newStatus, setNewStatus] = React.useState<Report['status'] | null>(null);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchReports = async () => {
      try {
        const fetchedAlerts = await getAlerts('BMC');
        const potholeReports = fetchedAlerts
          .filter(a => a.type === 'Pothole')
          .map(a => ({
            ...a,
            severity: a.severity as Report['severity'],
            status: a.status as Report['status']
          }));
        setReports(potholeReports);
      } catch (error) {
        console.error("Failed to fetch reports:", error);
        toast({
          title: "Error",
          description: "Could not fetch pothole reports from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchReports();
  }, [toast]);

  const handleViewDetails = (report: Report) => {
    setSelectedReport(report);
    setIsDetailsOpen(true);
  };

  const handleUpdateStatus = (report: Report) => {
    setSelectedReport(report);
    setNewStatus(report.status);
    setIsUpdateOpen(true);
  };
  
  const handleDispatchCrew = (reportId: string) => {
    toast({
        title: "Crew Dispatched",
        description: `A repair crew has been dispatched for report #${reportId}.`,
    });
  }

  const confirmUpdateStatus = async () => {
    if (selectedReport && newStatus) {
      try {
        await updateAlertStatus(selectedReport.id, newStatus);
        setReports(prevReports =>
            prevReports.map(r =>
            r.id === selectedReport.id ? { ...r, status: newStatus } : r
            )
        );
        toast({
            title: "Status Updated",
            description: `Report #${selectedReport.id} status has been updated to "${newStatus}".`,
        });
      } catch (error) {
        console.error("Failed to update status:", error);
        toast({
            title: "Error",
            description: "Could not update report status.",
            variant: "destructive",
        });
      }
    }
    setIsUpdateOpen(false);
    setSelectedReport(null);
  };
  
  const getSeverityBadgeVariant = (severity: Report['severity']) => {
    switch (severity) {
        case 'High': return 'destructive';
        case 'Medium': return 'default';
        case 'Low': return 'secondary';
        default: return 'default';
    }
  }

  const getStatusBadgeVariant = (status: Report['status']) => {
    switch (status) {
        case 'New': return 'destructive';
        case 'Work in Progress': return 'default';
        case 'Resolved': return 'secondary';
        default: return 'default';
    }
  }


  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Pothole Reports</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Wrench /> All Pothole Reports</CardTitle>
            <CardDescription>A log of all reported potholes and their repair status from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Report ID</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Severity</TableHead>
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
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  reports.map((report) => (
                    <TableRow key={report.id}>
                      <TableCell className="font-medium">{report.id}</TableCell>
                      <TableCell>{report.location}</TableCell>
                      <TableCell>
                        <Badge variant={getSeverityBadgeVariant(report.severity)}>
                          {report.severity}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={getStatusBadgeVariant(report.status)}>
                          {report.status}
                        </Badge>
                      </TableCell>
                      <TableCell>{report.date}</TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleViewDetails(report)}>View Details</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleDispatchCrew(report.id)}>Dispatch Crew</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleUpdateStatus(report)}>Update Status</DropdownMenuItem>
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
            <DialogTitle>Report Details: {selectedReport?.id}</DialogTitle>
            <DialogDescription>
              A detailed view of the pothole report.
            </DialogDescription>
          </DialogHeader>
          {selectedReport && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedReport.location}</p>
              </div>
               <div>
                <h4 className="font-semibold">Date Reported</h4>
                <p>{selectedReport.date}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedReport.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
      
      {/* Update Status Dialog */}
      <AlertDialog open={isUpdateOpen} onOpenChange={setIsUpdateOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Update Report Status</AlertDialogTitle>
            <AlertDialogDescription>
              Select the new status for report <strong>{selectedReport?.id}</strong>.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="py-4">
            <Select onValueChange={(value: Report['status']) => setNewStatus(value)} defaultValue={selectedReport?.status}>
                <SelectTrigger>
                    <SelectValue placeholder="Select a status" />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="New">New</SelectItem>
                    <SelectItem value="Work in Progress">Work in Progress</SelectItem>
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
