
"use client";

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Bot } from "lucide-react";
import { useToast } from '@/hooks/use-toast';
import { auth } from '@/lib/firebase';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';

const userRoles: { [key: string]: string } = {
  'super.admin@blr.gov.in': '/super-admin',
  'police@blr.gov.in': '/police',
  'bmc@blr.gov.in': '/bmc',
  'fire@blr.gov.in': '/fire',
  'ndrf@blr.gov.in': '/ndrf',
  'traffic@blr.gov.in': '/traffic',
  'moderator@blr.gov.in': '/moderator',
  'community@blr.gov.in': '/community',
  'hospital@blr.gov.in': '/hospitals',
};

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const router = useRouter();
  const { toast } = useToast();

  const handleLogin = () => {
    // In a real app, you'd have authentication logic here.
    const targetDashboard = userRoles[email.toLowerCase()];
    
    if (targetDashboard) {
      router.push(targetDashboard);
    } else {
       // Default redirect for any other user for prototype purposes
       router.push('/community');
    }
  };

  const handleGoogleSignIn = async () => {
    const provider = new GoogleAuthProvider();
    try {
      const result = await signInWithPopup(auth, provider);
      const user = result.user;
      
      toast({
          title: "Sign In Successful",
          description: `Welcome, ${user.displayName}!`,
      });

      // Check if the signed-in user's email has a specific role
      const targetDashboard = userRoles[user.email?.toLowerCase() || ''];
      if (targetDashboard) {
        router.push(targetDashboard);
      } else {
        // Default redirect for any other user
        router.push('/community');
      }
    } catch (error: any) {
      console.error("Error during Google sign-in:", error);
      let errorMessage = "An unknown error occurred. Please try again.";
      if (error.code) {
        errorMessage = `Could not sign in. (Reason: ${error.code})`;
      }
      toast({
        title: "Sign In Failed",
        description: errorMessage,
        variant: "destructive",
      });
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <Card className="w-full max-w-sm">
        <CardHeader className="text-center">
            <div className="flex justify-center mb-2">
                <div className="h-12 w-12 shrink-0 rounded-full bg-primary text-primary-foreground flex items-center justify-center">
                    <Bot className="h-8 w-8"/>
                </div>
            </div>
          <CardTitle className="text-2xl">Namma Nagara Login</CardTitle>
          <CardDescription>Enter your credentials or sign in with Google.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <Button variant="outline" onClick={handleGoogleSignIn}>
            Sign In with Google
          </Button>
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
                <span className="bg-background px-2 text-muted-foreground">Or continue with</span>
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="email">Email</Label>
            <Input 
                id="email" 
                type="email" 
                placeholder="admin@example.com" 
                required 
                value={email}
                onChange={(e) => setEmail(e.target.value)}
            />
          </div>
        </CardContent>
        <CardFooter>
          <Button className="w-full" onClick={handleLogin}>Sign In</Button>
        </CardFooter>
      </Card>
    </div>
  );
}
