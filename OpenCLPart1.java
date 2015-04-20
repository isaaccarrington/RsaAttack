import static org.jocl.CL.*;

import java.util.ArrayList;

import org.jocl.*;

/**
 * Concurrent Programming Assignment
 * Student: Isaac Carrington
 * Student ID: 04329228
 */
public class OpenCLPart1
{
    /**
     * The source code of the OpenCL program to execute
     */
    private static String programSource =
            "__kernel void "+
                    "factorKernel(__global const long *a,"+
                    "             __global long *b,"+
                    "			  __global long *c)"+	
                    "{"+
                    "   int gid = get_global_id(0);"+
                    // Obtain the arguments from the array c
                    "	long squareRootN = c[0];"+ // The square root of the number
                    "	long number = c[1];"+ // The number
                    "	long workItemRange = c[2];"+
                    // i variable is where the for loop will start
                    "	long i = a[gid];"+
                    "	long j;"+
                    /*
                     * k variable is where for loop will finish
                     * as long as it is less than squareRootN
                     */
                    "	long k=i+workItemRange;"+
                    "	if ( k > squareRootN){" +
                    "		j = squareRootN;"+
                    "	} else {" +
                    "		j = i+workItemRange;"+
                    "	}"+
                    // for loop to find prime factor
                    "	long f;"+
                    "	for (f=i; f<=j; f+=2){"+
                    "		if(number%f==0){"+
                    "			b[gid]=f;"+ // if the number is found, add to dstArray and break
                    "			break;"+
                    " 		}"+
                    "	}"+
                    "}";
        

    /**
     * The entry point to my fantastic programme 
     * 
     */
    public static void main(String args[])
    {
        long numBytes[] = new long[1];
        
        // Create input- and output data 
        String n = args[0];
        long number = Long.valueOf(n);
        long squareRootN = (long) Math.sqrt(number);       
        /*
         * 
         */
        int workItems = 120000; // 100,000 work items
        long workGroupRange = squareRootN/1000; // The range over which a work group will do work on
        long workItemRange = workGroupRange/120L; // The number range over which a work item will do work
        
        
        // Create Arrays
        long srcArrayA[] = new long[workItems+2];
        long dstArray[] = new long[workItems+2];
        long parArray[] = new long[3]; //array that will contain arguments of type long
        parArray[0] = squareRootN;
        parArray[1] = number;
        parArray[2] = workItemRange;
        
        // Insert data into srcArrayA
        int index = 1;
        srcArrayA[0] = 3; 
        for (long i=workItemRange; i<squareRootN; i=i+workItemRange)
        {
        	if(i%2==0){ // make sure that entry is an odd number
        		i--;
        	}
            srcArrayA[index] = i; 
            index++;
        }

        // create pointers to arrays
        Pointer srcA = Pointer.to(srcArrayA);
        Pointer dst = Pointer.to(dstArray);
        Pointer par = Pointer.to(parArray);

        // Obtain the platform IDs and initialize the context properties
        System.out.println("Obtaining platform...");
        
        cl_platform_id platforms[] = new cl_platform_id[1];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platforms[0]);
        
        // Create an OpenCL context on a GPU device
        cl_context context = clCreateContextFromType(
            contextProperties, CL_DEVICE_TYPE_GPU, null, null, null);

        if (context == null)
        {
            // If no context for a GPU device could be created,
            // try to create one for a CPU device.
            context = clCreateContextFromType(
                contextProperties, CL_DEVICE_TYPE_CPU, null, null, null);
            if (context == null)
            {
                System.out.println("Unable to create a context");
                return;
            }
        }

        // Enable exceptions and subsequently omit error checks in this sample
        CL.setExceptionsEnabled(true);
        
        // Get the list of GPU devices associated with the context
        clGetContextInfo(context, CL_CONTEXT_DEVICES, 0, null, numBytes); 
        
        // Obtain the cl_device_id for the first device
        int numDevices = (int) numBytes[0] / Sizeof.cl_device_id;
        cl_device_id devices[] = new cl_device_id[numDevices];
        clGetContextInfo(context, CL_CONTEXT_DEVICES, numBytes[0],  
            Pointer.to(devices), null);

        // Create a command-queue
        cl_command_queue commandQueue = 
            clCreateCommandQueue(context, devices[0], 0, null);

        // Allocate the memory objects for the input- and output data
        cl_mem memObjects[] = new cl_mem[3];
        // srcArrayA buffer
        memObjects[0] = clCreateBuffer(context, 
        	CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
        	Sizeof.cl_long * (workItems+2), srcA, null); 
        // dstArray buffer
        memObjects[1] = clCreateBuffer(context, 
            CL_MEM_READ_WRITE,
            Sizeof.cl_long * (workItems+2), null, null);
        // parArray buffer
        memObjects[2] = clCreateBuffer(context, 
        	CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_long * 3, par, null);
        
        // Create the program from the source code
        cl_program program = clCreateProgramWithSource(context,
            1, new String[]{ programSource }, null, null);
        
        // Build the program
        clBuildProgram(program, 0, null, null, null, null);
        
        // Create the kernel
        cl_kernel kernel = clCreateKernel(program, "factorKernel", null);
        
        // Set the arguments for the kernel
        clSetKernelArg(kernel, 0, 
            Sizeof.cl_mem, Pointer.to(memObjects[0]));
        clSetKernelArg(kernel, 1, 
            Sizeof.cl_mem, Pointer.to(memObjects[1]));
        clSetKernelArg(kernel, 2, 
            Sizeof.cl_mem, Pointer.to(memObjects[2]));


        
        // Set the work-item dimensions
        long global_work_size[] = new long[]{120000}; // NDRange is 120,000
        long local_work_size[] = new long[]{120}; // 120 work items in each work group
        
        // Execute the kernel
        clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
            global_work_size, local_work_size, 0, null, null);
        
        // Read the output data
        clEnqueueReadBuffer(commandQueue, memObjects[1], CL_TRUE, 0,
            workItems * Sizeof.cl_long, dst, 0, null, null);
        
        // Release kernel, program, and memory objects
        clReleaseMemObject(memObjects[0]);
        clReleaseMemObject(memObjects[1]);

        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(commandQueue);
        clReleaseContext(context);
        
        // Find an entry in dstArray > 0
        long firstFactor = 0;
        for(long x:dstArray){
        	if(x>0){
            	firstFactor = x;
        	}
        }
        
        long secondFactor = number/firstFactor;
        System.out.println("The number entered is: "+number);
        System.out.println("Prime factors for this number are "+firstFactor+" and "+secondFactor);
     
    }
}
